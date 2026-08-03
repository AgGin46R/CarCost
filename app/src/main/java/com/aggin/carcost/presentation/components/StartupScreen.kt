package com.aggin.carcost.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Экран запуска — продолжение системной заставки, тем же фоном и той же иконкой.
 *
 * Показывается, только пока приложение решает, что открыть: прочитаны ли
 * настройки и восстановлена ли сессия. Это местные операции на десятки
 * миллисекунд, поэтому в обычной жизни экран мелькает или не появляется вовсе.
 *
 * Ждать здесь сеть намеренно нечего. Данные лежат в локальной базе и
 * показываются сразу; свежие подъезжают следом и дорисовываются на месте.
 * Держать человека перед логотипом, пока ходят запросы, значит делать запуск
 * заложником связи — в метро приложение просто не открывалось бы.
 *
 * Цвет фона совпадает с windowSplashScreenBackground: переход от системной
 * заставки к этому экрану не должен быть заметен.
 */
@Composable
fun StartupScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1565C0)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsCar,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = Color.White
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "CarCost",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(32.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Color.White.copy(alpha = 0.8f),
                strokeWidth = 3.dp
            )
        }
    }
}
