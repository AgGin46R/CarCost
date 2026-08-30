package com.aggin.carcost.presentation.screens.chat

import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlin.math.abs

/**
 * Просмотр изображения с приближением.
 *
 * Раньше фотография открывалась картинкой во весь экран, и на этом всё:
 * приблизить её было нельзя, а любое касание закрывало просмотр. Разглядеть
 * номер на фотографии чека или мелкую надпись на детали было невозможно —
 * приходилось сохранять снимок и открывать его в галерее.
 *
 * Что делает здесь каждый жест:
 * - **щипок** — плавное приближение до пятикратного;
 * - **двойное касание** — переключение между обычным размером и двукратным,
 *   так быстрее всего рассмотреть деталь одной рукой;
 * - **перетаскивание** — сдвиг приближённого изображения;
 * - **одиночное касание** — закрыть, но **только когда не приближено**.
 *   Иначе выход происходил при каждой попытке подвинуть картинку.
 */
@Composable
fun ZoomableImage(
    url: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        val maxWidthPx = constraints.maxWidth.toFloat()
        val maxHeightPx = constraints.maxHeight.toFloat()

        /**
         * Не даём утащить картинку за пределы экрана.
         *
         * Без ограничения приближённое изображение уезжает в пустоту, и человек
         * теряет его совсем — вернуть можно только закрыв просмотр.
         */
        fun clampOffsets() {
            val maxX = (maxWidthPx * (scale - 1f)) / 2f
            val maxY = (maxHeightPx * (scale - 1f)) / 2f
            offsetX = offsetX.coerceIn(-maxX, maxX)
            offsetY = offsetY.coerceIn(-maxY, maxY)
        }

        fun reset() {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        }

        AsyncImage(
            model = url,
            contentDescription = stringResource(R.string.chat_foto),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                            clampOffsets()
                        } else {
                            // Вернулись к обычному размеру — картинка снова по центру
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) reset() else scale = 2f
                        },
                        onTap = {
                            // Закрываем только в обычном размере: иначе просмотр
                            // захлопывался при каждой попытке подвинуть картинку
                            if (abs(scale - 1f) < 0.01f) onDismiss()
                        }
                    )
                }
        )
    }
}
