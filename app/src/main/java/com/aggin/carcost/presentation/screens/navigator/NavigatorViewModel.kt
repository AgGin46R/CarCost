package com.aggin.carcost.presentation.screens.navigator

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.FavoritePlace
import com.aggin.carcost.data.local.database.entities.FavoritePlaceType
import com.aggin.carcost.data.navigation.NavigationService
import com.yandex.mapkit.RequestPoint
import com.yandex.mapkit.RequestPointType
import com.yandex.mapkit.directions.DirectionsFactory
import com.yandex.mapkit.directions.driving.DrivingOptions
import com.yandex.mapkit.directions.driving.DrivingRoute
import com.yandex.mapkit.directions.driving.DrivingRouterType
import com.yandex.mapkit.directions.driving.DrivingSession
import com.yandex.mapkit.directions.driving.VehicleOptions
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.search.Response
import com.yandex.mapkit.search.SearchFactory
import com.yandex.mapkit.search.SearchManagerType
import com.yandex.mapkit.search.SearchOptions
import com.yandex.mapkit.search.SearchType
import com.yandex.mapkit.search.Session
import com.yandex.runtime.Error
import com.yandex.runtime.network.NetworkError
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class NavigatorMode { IDLE, SEARCHING, ROUTE_READY, NAVIGATING, ARRIVED }

data class TripStats(
    val todayKm: Double = 0.0,
    val weekKm: Double = 0.0,
    val monthKm: Double = 0.0,
    val todayCount: Int = 0,
    val weekCount: Int = 0
)

/** A resolved search result used for the suggestions dropdown */
data class PlaceSuggestion(
    val name: String,
    val address: String = "",
    val point: Point? = null
)

data class PoiItem(
    val name: String,
    val address: String,
    val point: Point,
    val category: PoiCategory
)

enum class PoiCategory(val label: String, val query: String) {
    GAS_STATION("Заправки", "АЗС"),
    SERVICE("Сервис", "автосервис"),
    PARKING("Парковки", "парковка"),
    CAFE("Кафе", "кафе ресторан"),
    BANK("Банкомат", "банкомат банк"),
    SUPERMARKET("Магазин", "супермаркет")
}

data class NavigatorUiState(
    val mode: NavigatorMode = NavigatorMode.IDLE,
    val query: String = "",
    val suggestions: List<PlaceSuggestion> = emptyList(),
    val destinationPoint: Point? = null,
    val destinationName: String = "",
    val currentRoute: DrivingRoute? = null,
    val allRoutes: List<DrivingRoute> = emptyList(),
    val selectedRouteIndex: Int = 0,
    val routeDistanceKm: Double? = null,
    val routeTimeMin: Int? = null,
    val etaString: String = "",               // e.g. "14:38"
    val fuelCostEstimate: Double? = null,
    val fuelConsumptionDisplay: String? = null,  // e.g. "8.5 л/100км"
    val currentSpeedKmh: Int = 0,
    val currentLat: Double? = null,
    val currentLon: Double? = null,
    val currentBearing: Float = 0f,
    val isCameraLocked: Boolean = true,       // false = user panned away
    val favorites: List<FavoritePlace> = emptyList(),
    val cars: List<Car> = emptyList(),
    val selectedCarId: String = "",
    val poiItems: List<PoiItem> = emptyList(),
    val activePoiCategory: PoiCategory? = null,
    val isLoadingRoute: Boolean = false,
    val errorMessage: String? = null,
    val tripStats: TripStats? = null
)

class NavigatorViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val favoritePlaceDao = db.favoritePlaceDao()
    private val carDao = db.carDao()
    private val expenseDao = db.expenseDao()
    private val gpsTripDao = db.gpsTripDao()

    private var speaker: YandexSpeaker? = null

    fun initSpeaker(context: Context) {
        if (speaker == null) speaker = YandexSpeaker(context)
    }

    private var lastDeviationCheckMs = 0L
    private val DEVIATION_CHECK_INTERVAL = 10_000L
    private val DEVIATION_THRESHOLD_M = 150.0

    private val searchManager by lazy {
        SearchFactory.getInstance().createSearchManager(SearchManagerType.COMBINED)
    }
    private val drivingRouter by lazy {
        DirectionsFactory.getInstance().createDrivingRouter(DrivingRouterType.COMBINED)
    }

    private var suggestSearchSession: Session? = null
    private var reverseGeoSession: Session? = null
    private var drivingSession: DrivingSession? = null
    private var poiSearchSession: Session? = null
    private var debounceJob: Job? = null

    private val _uiState = MutableStateFlow(NavigatorUiState())
    val uiState: StateFlow<NavigatorUiState> = _uiState.asStateFlow()

    // Continuous GPS tracking
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                val speedKmh = (loc.speed * 3.6f).toInt()
                _uiState.update {
                    it.copy(
                        currentLat = loc.latitude,
                        currentLon = loc.longitude,
                        currentBearing = loc.bearing,
                        currentSpeedKmh = speedKmh
                    )
                }
                // Check route deviation while navigating
                if (_uiState.value.mode == NavigatorMode.NAVIGATING) {
                    checkRouteDeviation(loc.latitude, loc.longitude)
                }
            }
        }
    }

    init {
        loadData()
        startLocationTracking()
    }

    private fun loadData() {
        viewModelScope.launch {
            carDao.getAllCars().collect { cars ->
                val primaryCar = cars.firstOrNull { it.isActive } ?: cars.firstOrNull()
                val carId = primaryCar?.id ?: ""
                _uiState.update { it.copy(cars = cars, selectedCarId = carId) }
                if (carId.isNotBlank()) loadTripStats(carId)
            }
        }
        viewModelScope.launch {
            favoritePlaceDao.getAllFavoritePlaces().collect { places ->
                _uiState.update { it.copy(favorites = places) }
            }
        }
    }

    private fun loadTripStats(carId: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val startOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val startOfWeek = now - 7 * 86_400_000L
            val startOfMonth = now - 30 * 86_400_000L

            val todayTrips = gpsTripDao.getTripsSince(carId, startOfDay).firstOrNull() ?: emptyList()
            val weekTrips = gpsTripDao.getTripsSince(carId, startOfWeek).firstOrNull() ?: emptyList()
            val monthTrips = gpsTripDao.getTripsSince(carId, startOfMonth).firstOrNull() ?: emptyList()

            _uiState.update {
                it.copy(
                    tripStats = TripStats(
                        todayKm = todayTrips.sumOf { t -> t.distanceKm },
                        weekKm = weekTrips.sumOf { t -> t.distanceKm },
                        monthKm = monthTrips.sumOf { t -> t.distanceKm },
                        todayCount = todayTrips.size,
                        weekCount = weekTrips.size
                    )
                )
            }
        }
    }

    private fun startLocationTracking() {
        val req = LocationRequest.Builder(2_000L)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateDistanceMeters(3f)
            .build()
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
        } catch (_: SecurityException) {
        } catch (_: Exception) {}
    }

    fun retryLocationTracking() {
        startLocationTracking()
    }

    // ── Search ───────────────────────────────────────────────────────────────

    fun onQueryChanged(query: String) {
        _uiState.update {
            it.copy(
                query = query,
                mode = if (query.isBlank()) NavigatorMode.IDLE else NavigatorMode.SEARCHING
            )
        }
        debounceJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(suggestions = emptyList()) }
            return
        }
        debounceJob = viewModelScope.launch {
            delay(300)
            requestSuggestions(query)
        }
    }

    private fun requestSuggestions(query: String) {
        suggestSearchSession?.cancel()
        val userLat = _uiState.value.currentLat ?: 56.0097  // Krasnoyarsk fallback
        val userLon = _uiState.value.currentLon ?: 92.8664
        val delta = 1.5
        val localBbox = com.yandex.mapkit.geometry.BoundingBox(
            Point((userLat - delta).coerceAtLeast(-90.0), (userLon - delta).coerceAtLeast(-180.0)),
            Point((userLat + delta).coerceAtMost(90.0), (userLon + delta).coerceAtMost(180.0))
        )
        val searchOpts = SearchOptions().apply {
            searchTypes = SearchType.GEO.value or SearchType.BIZ.value
            resultPageSize = 7
        }
        suggestSearchSession = searchManager.submit(
            query,
            com.yandex.mapkit.geometry.Geometry.fromBoundingBox(localBbox),
            searchOpts,
            object : Session.SearchListener {
                override fun onSearchResponse(response: Response) {
                    val items = response.collection.children.mapNotNull { child ->
                        val obj = child.obj ?: return@mapNotNull null
                        val name = obj.name ?: return@mapNotNull null
                        val point = obj.geometry.firstOrNull()?.point
                        // Try to get a readable subtitle from metadata
                        val address = obj.descriptionText ?: ""
                        PlaceSuggestion(name = name, address = address, point = point)
                    }
                    _uiState.update { it.copy(suggestions = items) }
                }
                override fun onSearchError(error: Error) {
                    _uiState.update { it.copy(suggestions = emptyList()) }
                }
            }
        )
    }

    fun onSuggestionSelected(item: PlaceSuggestion) {
        _uiState.update { it.copy(query = item.name, suggestions = emptyList()) }
        val point = item.point
        if (point != null) {
            setDestination(point, item.name)
        } else {
            val searchOpts = SearchOptions().apply {
                searchTypes = SearchType.GEO.value or SearchType.BIZ.value
                resultPageSize = 1
            }
            searchManager.submit(
                item.name,
                com.yandex.mapkit.geometry.Geometry.fromBoundingBox(
                    com.yandex.mapkit.geometry.BoundingBox(
                        Point(41.2, 19.6), Point(82.0, 190.0)
                    )
                ),
                searchOpts,
                object : Session.SearchListener {
                    override fun onSearchResponse(response: Response) {
                        val geo = response.collection.children.firstOrNull()?.obj
                            ?.geometry?.firstOrNull()?.point
                        if (geo != null) setDestination(geo, item.name)
                        else _uiState.update { it.copy(errorMessage = "Не удалось найти место") }
                    }
                    override fun onSearchError(error: Error) {
                        _uiState.update { it.copy(errorMessage = "Не удалось найти место") }
                    }
                }
            )
        }
    }

    /** Called when user long-presses the map — reverse-geocodes the tapped point. */
    fun setDestinationFromMap(point: Point) {
        _uiState.update {
            it.copy(
                query = "Определяется адрес…",
                suggestions = emptyList(),
                isLoadingRoute = true,
                mode = NavigatorMode.SEARCHING
            )
        }
        reverseGeoSession?.cancel()
        val searchOpts = SearchOptions().apply {
            searchTypes = SearchType.GEO.value
            resultPageSize = 1
        }
        reverseGeoSession = searchManager.submit(
            point,
            17,
            searchOpts,
            object : Session.SearchListener {
                override fun onSearchResponse(response: Response) {
                    val name = response.collection.children.firstOrNull()?.obj?.name
                        ?: "%.4f, %.4f".format(point.latitude, point.longitude)
                    _uiState.update { it.copy(query = name) }
                    setDestination(point, name)
                }
                override fun onSearchError(error: Error) {
                    val name = "%.4f, %.4f".format(point.latitude, point.longitude)
                    _uiState.update { it.copy(query = name) }
                    setDestination(point, name)
                }
            }
        )
    }

    // ── Route ────────────────────────────────────────────────────────────────

    fun setDestination(point: Point, name: String) {
        _uiState.update {
            it.copy(
                destinationPoint = point,
                destinationName = name,
                isLoadingRoute = true,
                mode = NavigatorMode.SEARCHING,
                suggestions = emptyList()
            )
        }
        buildRoute(point)
    }

    private fun buildRoute(destination: Point) {
        val currentLat = _uiState.value.currentLat ?: 56.0097
        val currentLon = _uiState.value.currentLon ?: 92.8664
        val from = Point(currentLat, currentLon)

        drivingSession?.cancel()
        val requestPoints = listOf(
            RequestPoint(from, RequestPointType.WAYPOINT, null, null, null),
            RequestPoint(destination, RequestPointType.WAYPOINT, null, null, null)
        )
        val drivingOptions = DrivingOptions().apply { routesCount = 3 }
        drivingSession = drivingRouter.requestRoutes(
            requestPoints,
            drivingOptions,
            VehicleOptions(),
            object : DrivingSession.DrivingRouteListener {
                override fun onDrivingRoutes(routes: MutableList<DrivingRoute>) {
                    if (routes.isNotEmpty()) {
                        val route = routes[0]
                        val distKm = route.metadata.weight.distance.value / 1000.0
                        val timeSec = route.metadata.weight.timeWithTraffic.value
                        val timeMin = (timeSec / 60).toInt()
                        val eta = computeEta(timeMin)
                        _uiState.update {
                            it.copy(
                                currentRoute = route,
                                allRoutes = routes.toList(),
                                selectedRouteIndex = 0,
                                routeDistanceKm = distKm,
                                routeTimeMin = timeMin,
                                etaString = eta,
                                isLoadingRoute = false,
                                mode = NavigatorMode.ROUTE_READY
                            )
                        }
                        estimateFuelCost(distKm)
                    }
                }
                override fun onDrivingRoutesError(error: Error) {
                    val msg = if (error is NetworkError)
                        "Нет сети для построения маршрута"
                    else
                        "Ошибка построения маршрута"
                    _uiState.update { it.copy(isLoadingRoute = false, errorMessage = msg) }
                }
            }
        )
    }

    private fun computeEta(timeMin: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MINUTE, timeMin)
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        return "%02d:%02d".format(h, m)
    }

    private fun estimateFuelCost(distanceKm: Double) {
        val carId = _uiState.value.selectedCarId.ifBlank { return }
        viewModelScope.launch {
            val refuels = expenseDao.getFullTankRefuels(carId, 10).firstOrNull() ?: emptyList()
            if (refuels.isNotEmpty()) {
                // Price per liter from recent fill-ups
                val totalLiters = refuels.sumOf { it.fuelLiters ?: 0.0 }
                val totalSpend = refuels.sumOf { it.amount }
                val pricePerLiter = if (totalLiters > 0) totalSpend / totalLiters else 55.0

                // Average consumption from consecutive full-tank pairs
                val sorted = refuels.sortedBy { it.date }
                val consumptions = sorted.zipWithNext { a, b ->
                    val km = (b.odometer - a.odometer).toDouble()
                    val lt = b.fuelLiters ?: 0.0
                    if (km > 30 && lt > 0) (lt / km) * 100.0 else null
                }.filterNotNull()
                    .filter { it in 2.0..30.0 }   // sanity filter

                val avgConsumption = if (consumptions.isNotEmpty()) consumptions.average() else 10.0
                val displayConsumption = "%.1f л/100км".format(avgConsumption)

                val cost = (distanceKm / 100.0 * avgConsumption) * pricePerLiter
                _uiState.update {
                    it.copy(
                        fuelCostEstimate = cost,
                        fuelConsumptionDisplay = displayConsumption
                    )
                }
            }
        }
    }

    // ── Navigation control ───────────────────────────────────────────────────

    fun startNavigation() {
        val dest = _uiState.value.destinationPoint ?: return
        val carId = _uiState.value.selectedCarId
        val destName = _uiState.value.destinationName

        speaker?.speak("Начинаем маршрут. $destName")

        val intent = Intent(getApplication(), NavigationService::class.java).apply {
            action = NavigationService.ACTION_START
            putExtra(NavigationService.EXTRA_CAR_ID, carId)
            putExtra(NavigationService.EXTRA_DEST_LAT, dest.latitude)
            putExtra(NavigationService.EXTRA_DEST_LON, dest.longitude)
            putExtra(NavigationService.EXTRA_DEST_NAME, destName)
        }
        getApplication<Application>().startForegroundService(intent)
        _uiState.update { it.copy(mode = NavigatorMode.NAVIGATING, isCameraLocked = true) }
    }

    fun stopNavigation() {
        val intent = Intent(getApplication(), NavigationService::class.java).apply {
            action = NavigationService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)
        _uiState.update { it.copy(mode = NavigatorMode.ARRIVED) }
    }

    fun clearDestination() {
        drivingSession?.cancel()
        _uiState.update {
            it.copy(
                mode = NavigatorMode.IDLE,
                query = "",
                suggestions = emptyList(),
                destinationPoint = null,
                destinationName = "",
                currentRoute = null,
                allRoutes = emptyList(),
                selectedRouteIndex = 0,
                routeDistanceKm = null,
                routeTimeMin = null,
                etaString = "",
                fuelCostEstimate = null,
                fuelConsumptionDisplay = null,
                poiItems = emptyList(),
                activePoiCategory = null,
                isCameraLocked = true
            )
        }
    }

    /** Switch to a different alternative route by index. */
    fun selectRoute(index: Int) {
        val routes = _uiState.value.allRoutes
        if (index !in routes.indices) return
        val route = routes[index]
        val distKm = route.metadata.weight.distance.value / 1000.0
        val timeSec = route.metadata.weight.timeWithTraffic.value
        val timeMin = (timeSec / 60).toInt()
        _uiState.update {
            it.copy(
                selectedRouteIndex = index,
                currentRoute = route,
                routeDistanceKm = distKm,
                routeTimeMin = timeMin,
                etaString = computeEta(timeMin)
            )
        }
        estimateFuelCost(distKm)
    }

    // ── Route deviation ──────────────────────────────────────────────────────

    private fun checkRouteDeviation(lat: Double, lon: Double) {
        val now = System.currentTimeMillis()
        if (now - lastDeviationCheckMs < DEVIATION_CHECK_INTERVAL) return
        lastDeviationCheckMs = now

        val route = _uiState.value.currentRoute ?: return
        val pts = route.geometry.points
        if (pts.isEmpty()) return

        val minDist = pts.minOf { pt -> haversineMeters(lat, lon, pt.latitude, pt.longitude) }
        if (minDist > DEVIATION_THRESHOLD_M) {
            val dest = _uiState.value.destinationPoint ?: return
            speaker?.speak("Перестраиваю маршрут")
            buildRoute(dest)
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * asin(sqrt(a))
    }

    // ── Camera lock ──────────────────────────────────────────────────────────

    fun unlockCamera() {
        _uiState.update { it.copy(isCameraLocked = false) }
    }

    fun lockCamera() {
        _uiState.update { it.copy(isCameraLocked = true) }
    }

    // ── POI ─────────────────────────────────────────────────────────────────

    fun searchPoi(category: PoiCategory) {
        val dest = _uiState.value.destinationPoint
        val currentLat = _uiState.value.currentLat ?: 56.0097
        val currentLon = _uiState.value.currentLon ?: 92.8664
        val center = dest ?: Point(currentLat, currentLon)

        val activeCategory = if (_uiState.value.activePoiCategory == category) null else category
        if (activeCategory == null) {
            _uiState.update { it.copy(poiItems = emptyList(), activePoiCategory = null) }
            return
        }

        _uiState.update { it.copy(activePoiCategory = activeCategory) }
        val bbox = com.yandex.mapkit.geometry.BoundingBox(
            Point(center.latitude - 0.05, center.longitude - 0.05),
            Point(center.latitude + 0.05, center.longitude + 0.05)
        )
        val searchOpts = SearchOptions().apply {
            searchTypes = SearchType.BIZ.value
            resultPageSize = 10
        }
        poiSearchSession?.cancel()
        poiSearchSession = searchManager.submit(
            category.query,
            com.yandex.mapkit.geometry.Geometry.fromBoundingBox(bbox),
            searchOpts,
            object : Session.SearchListener {
                override fun onSearchResponse(response: Response) {
                    val items = response.collection.children.mapNotNull { child ->
                        val obj = child.obj ?: return@mapNotNull null
                        val point = obj.geometry.firstOrNull()?.point ?: return@mapNotNull null
                        PoiItem(obj.name ?: category.label, "", point, activeCategory)
                    }
                    _uiState.update { it.copy(poiItems = items) }
                }
                override fun onSearchError(error: Error) {
                    _uiState.update { it.copy(poiItems = emptyList()) }
                }
            }
        )
    }

    // ── Favorites ────────────────────────────────────────────────────────────

    fun saveFavoritePlace(name: String, lat: Double, lon: Double, type: FavoritePlaceType, address: String = "") {
        viewModelScope.launch {
            favoritePlaceDao.insertFavoritePlace(
                FavoritePlace(name = name, latitude = lat, longitude = lon, type = type, address = address)
            )
        }
    }

    fun deleteFavoritePlace(id: String) {
        viewModelScope.launch { favoritePlaceDao.deleteFavoritePlace(id) }
    }

    fun selectCar(carId: String) {
        _uiState.update { it.copy(selectedCarId = carId) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        suggestSearchSession?.cancel()
        reverseGeoSession?.cancel()
        drivingSession?.cancel()
        poiSearchSession?.cancel()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        speaker?.shutdown()
    }
}
