package com.aggin.carcost.presentation.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

/**
 * Переход, который не срабатывает дважды от одного намерения.
 *
 * Проблема, которую это решает. Переходы между экранами анимируются 300 мс, и
 * всё это время старый экран продолжает принимать нажатия. Двойное касание —
 * привычное действие, а не редкость — клало в стек два одинаковых экрана.
 * На «Добавить расход» это стоило дорого: каждый экземпляр формы заводит свой
 * идентификатор записи, поэтому заполнение обоих бланков давало ДВА расхода.
 * А если заполнить один, `popBackStack()` возвращал на второй, пустой, — и
 * выглядело это как «запись не сохранилась».
 *
 * Проверка идёт по состоянию жизненного цикла текущей записи стека: пока экран
 * действительно на переднем плане (RESUMED), переход разрешён. Как только
 * начался уход с экрана, состояние падает ниже, и повторное нажатие
 * игнорируется. Это надёжнее сравнения маршрутов: ловит и двойной тап по одной
 * кнопке, и быстрые нажатия по двум разным.
 *
 * `launchSingleTop` дополнительно защищает от повторного открытия того же
 * маршрута, если событие всё же проскочило.
 */
fun NavController.navigateOnce(
    route: String,
    builder: NavOptionsBuilder.() -> Unit = {}
) {
    val entry = currentBackStackEntry
    if (entry != null && entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        navigate(route) {
            launchSingleTop = true
            builder()
        }
    }
}
