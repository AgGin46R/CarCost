package com.aggin.carcost.presentation.screens.navigator

import android.app.Application
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

enum class NavigatorMode { IDLE, SEARCHING, ROUTE_READY, NAVIGATING, ARRIVED }

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
    PARKING("Парковки", "парковка")
}

data class NavigatorUiState(
    val mode: NavigatorMode = NavigatorMode.IDLE,
    val query: String = "",
    val suggestions: List<PlaceSuggestion> = emptyList(),
    val destinationPoint: Point? = null,
    val destinationName: String = "",
    val currentRoute: DrivingRoute? = null,
    val routeDistanceKm: Double? = null,
    val routeTimeMin: Int? = null,
    val fuelCostEstimate: Double? = null,
    val currentSpeedKmh: Int = 0,
    val currentLat: Double? = null,
    val currentLon: Double? = null,
    val currentBearing: Float = 0f,
    val favorites: List<FavoritePlace> = emptyList(),
    val cars: List<Car> = emptyList(),
    val selectedCarId: String = "",
    val poiItems: List<PoiItem> = emptyList(),
    val activePoiCategory: PoiCategory? = null,
    val isLoadingRoute: Boolean = false,
    val errorMessage: String? = null
)

class NavigatorViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val favoritePlaceDao = db.favoritePlaceDao()
    private val carDao = db.carDao()
    private val expenseDao = db.expenseDao()

    private val searchManager by lazy {
        SearchFactory.getInstance().createSearchManager(SearchManagerType.COMBINED)
    }
    private val drivingRouter by lazy {
        DirectionsFactory.getInstance().createDrivingRouter(DrivingRouterType.COMBINED)
    }

    private var suggestSearchSession: Session? = null
    private var drivingSession: DrivingSession? = null
    private var poiSearchSession: Session? = null
    private var debounceJob: Job? = null

    private val _uiState = MutableStateFlow(NavigatorUiState())
    val uiState: StateFlow<NavigatorUiState> = _uiState.asStateFlow()

    // Continuous GPS tracking — used for search bias, camera follow, and speed display
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
                _uiState.update { it.copy(cars = cars, selectedCarId = primaryCar?.id ?: "") }
            }
        }
        viewModelScope.launch {
            favoritePlaceDao.getAllFavoritePlaces().collect { places ->
                _uiState.update { it.copy(favorites = places) }
            }
        }
    }

    private fun startLocationTracking() {
        val req = LocationRequest.Builder(2_000L)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateDistanceMeters(3f)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
        } catch (_: SecurityException) { /* permission not granted yet */ }
    }

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query, mode = if (query.isBlank()) NavigatorMode.IDLE else NavigatorMode.SEARCHING) }
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
        // Use user's actual position for local search bias (~150 km radius).
        // Fallback to Krasnoyarsk when GPS not yet acquired.
        val userLat = _uiState.value.currentLat ?: 56.0097
        val userLon = _uiState.value.currentLon ?: 92.8664
        val delta = 1.5  // ≈ 150 km
        val localBbox = com.yandex.mapkit.geometry.BoundingBox(
            Point((userLat - delta).coerceAtLeast(-90.0), (userLon - delta).coerceAtLeast(-180.0)),
            Point((userLat + delta).coerceAtMost(90.0),  (userLon + delta).coerceAtMost(180.0))
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
                        PlaceSuggestion(name = name, point = point)
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
            // Rare: result had no point — do a second resolve search
            val searchOpts = SearchOptions().apply {
                searchTypes = SearchType.GEO.value or SearchType.BIZ.value
                resultPageSize = 1
            }
            searchManager.submit(item.name, com.yandex.mapkit.geometry.Geometry.fromBoundingBox(
                com.yandex.mapkit.geometry.BoundingBox(Point(41.2, 19.6), Point(82.0, 190.0))
            ), searchOpts, object : Session.SearchListener {
                override fun onSearchResponse(response: Response) {
                    val geo = response.collection.children.firstOrNull()?.obj?.geometry?.firstOrNull()?.point
                    if (geo != null) setDestination(geo, item.name)
                    else _uiState.update { it.copy(errorMessage = "Не удалось найти место") }
                }
                override fun onSearchError(error: Error) {
                    _uiState.update { it.copy(errorMessage = "Не удалось найти место") }
                }
            })
        }
    }

    fun setDestination(point: Point, name: String) {
        _uiState.update {
            it.copy(
                destinationPoint = point,
                destinationName = name,
                isLoadingRoute = true,
                mode = NavigatorMode.SEARCHING
            )
        }
        buildRoute(point)
    }

    private fun buildRoute(destination: Point) {
        val currentLat = _uiState.value.currentLat ?: 55.7558  // Default: Moscow center
        val currentLon = _uiState.value.currentLon ?: 37.6173
        val from = Point(currentLat, currentLon)

        drivingSession?.cancel()
        val requestPoints = listOf(
            RequestPoint(from, RequestPointType.WAYPOINT, null, null, null),
            RequestPoint(destination, RequestPointType.WAYPOINT, null, null, null)
        )
        val drivingOptions = DrivingOptions().apply { routesCount = 1 }
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
                        _uiState.update {
                            it.copy(
                                currentRoute = route,
                                routeDistanceKm = distKm,
                                routeTimeMin = timeMin,
                                isLoadingRoute = false,
                                mode = NavigatorMode.ROUTE_READY
                            )
                        }
                        estimateFuelCost(distKm)
                    }
                }
                override fun onDrivingRoutesError(error: Error) {
                    val msg = if (error is NetworkError) "Нет сети для построения маршрута" else "Ошибка построения маршрута"
                    _uiState.update { it.copy(isLoadingRoute = false, errorMessage = msg) }
                }
            }
        )
    }

    private fun estimateFuelCost(distanceKm: Double) {
        val carId = _uiState.value.selectedCarId.ifBlank { return }
        viewModelScope.launch {
            val refuels = expenseDao.getFullTankRefuels(carId, 5).firstOrNull() ?: emptyList()
            if (refuels.isNotEmpty()) {
                // price per liter = total spend / total liters (or fallback 55 rub/L)
                val totalLiters = refuels.sumOf { it.fuelLiters ?: 0.0 }
                val totalSpend = refuels.sumOf { it.amount }
                val pricePerLiter = if (totalLiters > 0) totalSpend / totalLiters else 55.0
                // Assume ~10 L/100km as default consumption
                val avgConsumption = 10.0
                val fuelNeeded = distanceKm / 100.0 * avgConsumption
                val cost = fuelNeeded * pricePerLiter
                _uiState.update { it.copy(fuelCostEstimate = cost) }
            }
        }
    }

    fun startNavigation() {
        val dest = _uiState.value.destinationPoint ?: return
        val carId = _uiState.value.selectedCarId
        val destName = _uiState.value.destinationName

        val intent = Intent(getApplication(), NavigationService::class.java).apply {
            action = NavigationService.ACTION_START
            putExtra(NavigationService.EXTRA_CAR_ID, carId)
            putExtra(NavigationService.EXTRA_DEST_LAT, dest.latitude)
            putExtra(NavigationService.EXTRA_DEST_LON, dest.longitude)
            putExtra(NavigationService.EXTRA_DEST_NAME, destName)
        }
        getApplication<Application>().startForegroundService(intent)
        _uiState.update { it.copy(mode = NavigatorMode.NAVIGATING) }
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
                routeDistanceKm = null,
                routeTimeMin = null,
                fuelCostEstimate = null
            )
        }
    }

    fun searchPoi(category: PoiCategory) {
        val dest = _uiState.value.destinationPoint
        val currentLat = _uiState.value.currentLat ?: 55.7558
        val currentLon = _uiState.value.currentLon ?: 37.6173
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
                        val name = obj.name ?: category.label
                        PoiItem(name, "", point, activeCategory)
                    }
                    _uiState.update { it.copy(poiItems = items) }
                }
                override fun onSearchError(error: Error) {
                    _uiState.update { it.copy(poiItems = emptyList()) }
                }
            }
        )
    }

    fun saveFavoritePlace(name: String, lat: Double, lon: Double, type: FavoritePlaceType, address: String = "") {
        viewModelScope.launch {
            favoritePlaceDao.insertFavoritePlace(
                FavoritePlace(name = name, latitude = lat, longitude = lon, type = type, address = address)
            )
        }
    }

    fun deleteFavoritePlace(id: String) {
        viewModelScope.launch {
            favoritePlaceDao.deleteFavoritePlace(id)
        }
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
        drivingSession?.cancel()
        poiSearchSession?.cancel()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
