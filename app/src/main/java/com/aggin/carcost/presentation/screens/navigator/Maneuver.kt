package com.aggin.carcost.presentation.screens.navigator

import com.yandex.mapkit.directions.driving.DrivingRoute
import com.yandex.mapkit.geometry.Point
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Следующий манёвр на маршруте.
 *
 * До этого навигатор умел построить маршрут, нарисовать его и вести за ним
 * камеру — но не говорил, куда поворачивать. Ни на экране, ни голосом: озвучка
 * ограничивалась фразами «Начинаем маршрут» и «Перестраиваю маршрут». То есть
 * пользоваться им как навигатором за рулём было нельзя, приходилось смотреть
 * на линию и догадываться.
 *
 * @param action    что делать: поворот, разворот, съезд
 * @param distanceM сколько метров до манёвра
 * @param street    куда попадём после манёвра, если это известно
 */
data class Maneuver(
    val action: ManeuverAction,
    val distanceM: Int,
    val street: String?
) {
    /**
     * Подпись расстояния.
     *
     * Округляем тем грубее, чем дальше: «через 850 м» за рулём читается хуже,
     * чем «через 800 м», а разница в полсотни метров ни на что не влияет.
     */
    val distanceLabel: String
        get() = when {
            distanceM >= 1000 -> "%.1f км".format(distanceM / 1000.0)
            distanceM >= 100 -> "${(distanceM / 50) * 50} м"
            else -> "$distanceM м"
        }
}

/**
 * Виды манёвров, сведённые к тому, что реально нужно показать стрелкой.
 *
 * Полный перечень MapKit подробнее, но за рулём разница между «резко направо» и
 * «направо» на глаз не считывается, а лишние варианты только усложняют значок.
 */
enum class ManeuverAction(val label: String) {
    STRAIGHT("Прямо"),
    LEFT("Налево"),
    SLIGHT_LEFT("Левее"),
    HARD_LEFT("Резко налево"),
    RIGHT("Направо"),
    SLIGHT_RIGHT("Правее"),
    HARD_RIGHT("Резко направо"),
    U_TURN("Разворот"),
    FINISH("Прибытие")
}

/**
 * Ищет ближайший манёвр впереди.
 *
 * Положение на маршруте определяется ближайшей точкой линии — тем же способом,
 * которым экран делит пройденный и оставшийся путь. Отдельного отслеживания
 * позиции MapKit здесь не заводим: оно требует своей сессии навигации и на
 * порядок больше кода, а точности «ближайшей точки» для подсказки хватает.
 */
fun nextManeuver(route: DrivingRoute, lat: Double, lon: Double): Maneuver? {
    val points = route.geometry.points
    if (points.size < 2) return null

    val currentIndex = points.indices.minByOrNull { i ->
        val dLat = lat - points[i].latitude
        val dLon = lon - points[i].longitude
        dLat * dLat + dLon * dLon
    } ?: return null

    // Секции маршрута идут подряд; начало каждой — точка манёвра
    for (section in route.sections) {
        val begin = section.geometry.begin.segmentIndex
        if (begin <= currentIndex) continue

        val action = section.metadata.annotation.action?.toManeuverAction() ?: ManeuverAction.STRAIGHT
        val street = section.metadata.annotation.toponym?.takeIf { it.isNotBlank() }
        val distance = distanceAlong(points, currentIndex, begin)

        return Maneuver(action = action, distanceM = distance, street = street)
    }

    // Секции кончились — впереди только финиш
    return Maneuver(
        action = ManeuverAction.FINISH,
        distanceM = distanceAlong(points, currentIndex, points.lastIndex),
        street = null
    )
}

/** Длина участка линии между двумя точками, в метрах */
private fun distanceAlong(points: List<Point>, from: Int, to: Int): Int {
    if (to <= from) return 0
    var meters = 0.0
    for (i in from until minOf(to, points.lastIndex)) {
        meters += haversine(points[i], points[i + 1])
    }
    return meters.toInt()
}

private fun haversine(a: Point, b: Point): Double {
    val earthRadius = 6_371_000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
    return 2 * earthRadius * asin(sqrt(h))
}

/**
 * Сопоставление по названию, а не по константам перечисления.
 *
 * Набор действий у MapKit от версии к версии меняется — например, разворот
 * назывался то UTURN, то U_TURN. Жёсткая ссылка на константу ломает сборку при
 * обновлении библиотеки, а неизвестное значение здесь просто станет «прямо»:
 * подсказка окажется беднее, но приложение не сломается.
 */
private fun com.yandex.mapkit.directions.driving.Action.toManeuverAction(): ManeuverAction =
    when (name.uppercase()) {
        "LEFT" -> ManeuverAction.LEFT
        "SLIGHT_LEFT" -> ManeuverAction.SLIGHT_LEFT
        "HARD_LEFT" -> ManeuverAction.HARD_LEFT
        "RIGHT" -> ManeuverAction.RIGHT
        "SLIGHT_RIGHT" -> ManeuverAction.SLIGHT_RIGHT
        "HARD_RIGHT" -> ManeuverAction.HARD_RIGHT
        "UTURN", "U_TURN" -> ManeuverAction.U_TURN
        else -> ManeuverAction.STRAIGHT
    }

/**
 * Чем один вариант маршрута отличается от остальных.
 *
 * Яндекс нередко возвращает несколько маршрутов, одинаковых по времени и длине,
 * но идущих разными улицами. Подпись «4 км · 8 мин» на всех трёх вариантах
 * говорит правду и при этом бесполезна: она показывает ровно то, что у них
 * общее, а выбирать человеку приходится по тому, что различается.
 *
 * Поэтому ищем улицу, которая есть в этом маршруте и отсутствует в остальных,
 * и по ней его называем — «через Копылова». Так подписывают альтернативы все
 * карты, и так человек их и держит в голове.
 *
 * @return название улицы либо null, если отличить нечем
 */
fun distinguishingStreet(routes: List<DrivingRoute>, index: Int): String? {
    if (index !in routes.indices || routes.size < 2) return null

    /** Длина каждой улицы в маршруте: короткий проезд не должен побеждать проспект */
    fun streetLengths(route: DrivingRoute): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        for (section in route.sections) {
            val name = section.metadata.annotation.toponym?.takeIf { it.isNotBlank() } ?: continue
            val meters = section.metadata.weight.distance.value
            result[name] = (result[name] ?: 0.0) + meters
        }
        return result
    }

    val mine = streetLengths(routes[index])
    if (mine.isEmpty()) return null

    val others = routes.indices
        .filter { it != index }
        .flatMap { streetLengths(routes[it]).keys }
        .toSet()

    // Улица, которой нет у соседей, — самая длинная из таких
    val unique = mine.filterKeys { it !in others }
    val chosen = (unique.takeIf { it.isNotEmpty() } ?: mine)
        .maxByOrNull { it.value }?.key
        ?: return null

    return chosen
}
