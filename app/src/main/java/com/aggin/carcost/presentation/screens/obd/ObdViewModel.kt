package com.aggin.carcost.presentation.screens.obd

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.R
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.repository.CarRepository
import com.aggin.carcost.data.obd.ObdConnection
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Сопряжённый адаптер из списка системы */
data class ObdDevice(val name: String, val address: String)

/** Код неисправности вместе с расшифровкой */
data class DiagnosticCode(val code: String, val descriptionRes: Int, val isKnown: Boolean)

/**
 * Живые параметры.
 *
 * Каждое поле отдельно nullable: адаптер отвечает не на все запросы, а
 * набор поддерживаемых параметров у каждой машины свой. Отсутствие значения
 * показывается прочерком, а не нулём — ноль оборотов и «обороты не читаются»
 * это разные вещи.
 */
data class LiveData(
    val rpm: Int? = null,
    val speedKmh: Int? = null,
    val coolantTempC: Int? = null,
    val intakeTempC: Int? = null,
    val fuelLevel: Float? = null,
    val engineLoad: Float? = null,
    val voltage: Double? = null
)

data class ObdUiState(
    val bluetoothAvailable: Boolean = true,
    val devices: List<ObdDevice> = emptyList(),
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val connectedTo: String? = null,
    val live: LiveData = LiveData(),
    val codes: List<DiagnosticCode> = emptyList(),
    val codesRead: Boolean = false,
    val scanningCodes: Boolean = false,
    val vin: String? = null,
    /** Сообщение для человека: результат действия или ошибка */
    val message: String? = null
)

class ObdViewModel(
    application: Application,
    private val carId: String
) : AndroidViewModel(application) {

    private val connection = ObdConnection()
    private val db = AppDatabase.getDatabase(application)
    private val carRepository = CarRepository(db.carDao())
    private val supabaseCarRepo = SupabaseCarRepository(SupabaseAuthRepository())

    private val _uiState = MutableStateFlow(ObdUiState())
    val uiState: StateFlow<ObdUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    /**
     * Список сопряжённых устройств.
     *
     * Поиска новых устройств здесь нет намеренно: адаптер сопрягают один раз в
     * системных настройках Bluetooth, с вводом PIN-кода, и повторять этот
     * диалог внутри приложения — лишний путь, на котором легко ошибиться.
     */
    @SuppressLint("MissingPermission")
    fun loadDevices() {
        val manager = getApplication<Application>()
            .getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = manager?.adapter

        if (adapter == null || !adapter.isEnabled) {
            _uiState.value = _uiState.value.copy(
                bluetoothAvailable = false,
                devices = emptyList()
            )
            return
        }

        val devices = try {
            adapter.bondedDevices.orEmpty().map {
                ObdDevice(name = it.name ?: it.address, address = it.address)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Нет разрешения на список устройств", e)
            emptyList()
        }

        _uiState.value = _uiState.value.copy(bluetoothAvailable = true, devices = devices)
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(connecting = true, message = null)

            val manager = getApplication<Application>()
                .getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val device: BluetoothDevice? = try {
                manager?.adapter?.getRemoteDevice(address)
            } catch (e: Exception) {
                null
            }

            if (device == null) {
                _uiState.value = _uiState.value.copy(
                    connecting = false,
                    message = string(R.string.obd_connect_failed, "")
                )
                return@launch
            }

            val error = connection.connect(device)
            if (error != null) {
                _uiState.value = _uiState.value.copy(
                    connecting = false,
                    connected = false,
                    message = string(R.string.obd_connect_failed, error)
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                connecting = false,
                connected = true,
                connectedTo = _uiState.value.devices.firstOrNull { it.address == address }?.name
                    ?: address
            )
            startPolling()
        }
    }

    fun disconnect() {
        pollJob?.cancel()
        pollJob = null
        connection.disconnect()
        _uiState.value = _uiState.value.copy(
            connected = false,
            connectedTo = null,
            live = LiveData()
        )
    }

    /**
     * Опрос живых параметров.
     *
     * Запросы идут по одному и последовательно: шина однопоточная, и послать
     * второй запрос, не дождавшись ответа на первый, — верный способ получить
     * перемешанные ответы.
     */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && connection.isConnected) {
                val live = LiveData(
                    rpm = connection.readRpm(),
                    speedKmh = connection.readSpeed(),
                    coolantTempC = connection.readCoolantTemp(),
                    intakeTempC = connection.readIntakeTemp(),
                    fuelLevel = connection.readFuelLevel(),
                    engineLoad = connection.readEngineLoad(),
                    voltage = connection.readVoltage()
                )
                _uiState.value = _uiState.value.copy(live = live)
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun readCodes() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(scanningCodes = true, message = null)
            val codes = connection.readTroubleCodes().map { code ->
                DiagnosticCode(
                    code = code,
                    descriptionRes = com.aggin.carcost.data.obd.DtcCatalog.describe(code),
                    isKnown = com.aggin.carcost.data.obd.DtcCatalog.isKnown(code)
                )
            }
            _uiState.value = _uiState.value.copy(
                scanningCodes = false,
                codes = codes,
                codesRead = true
            )
        }
    }

    fun clearCodes() {
        viewModelScope.launch {
            val ok = connection.clearTroubleCodes()
            _uiState.value = _uiState.value.copy(
                codes = if (ok) emptyList() else _uiState.value.codes,
                message = string(if (ok) R.string.obd_codes_cleared else R.string.obd_clear_failed)
            )
        }
    }

    /**
     * Читает VIN и, если у машины он не заполнен, записывает.
     *
     * Существующий VIN не перезаписывается: человек мог ввести его из ПТС, а
     * блок управления в перебитой или отремонтированной машине иногда отдаёт
     * не то. Расхождение показываем, решение оставляем владельцу.
     */
    fun readVin() {
        viewModelScope.launch {
            val vin = connection.readVin()
            if (vin == null) {
                _uiState.value = _uiState.value.copy(
                    message = string(R.string.obd_vin_not_read)
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(vin = vin)
            val car = carRepository.getCarById(carId) ?: return@launch

            when {
                car.vin.isNullOrBlank() -> {
                    val updated = car.copy(vin = vin, updatedAt = System.currentTimeMillis())
                    carRepository.updateCar(updated)
                    launch(Dispatchers.IO) {
                        runCatching { supabaseCarRepo.updateCar(updated) }
                    }
                    _uiState.value = _uiState.value.copy(
                        message = string(R.string.obd_vin_saved, vin)
                    )
                }
                car.vin.equals(vin, ignoreCase = true) ->
                    _uiState.value = _uiState.value.copy(
                        message = string(R.string.obd_vin_matches)
                    )
                else ->
                    _uiState.value = _uiState.value.copy(
                        message = string(R.string.obd_vin_differs, vin, car.vin)
                    )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }

    private fun string(res: Int, vararg args: Any): String =
        getApplication<Application>().getString(res, *args)

    companion object {
        private const val TAG = "ObdViewModel"

        /**
         * Пауза между кругами опроса.
         *
         * Секунда: чаще не нужно — на экране всё равно не уследить, а шина
         * успевает отвечать без очереди.
         */
        private const val POLL_INTERVAL_MS = 1_000L
    }
}
