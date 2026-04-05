package com.aggin.carcost.presentation.screens.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.aggin.carcost.data.local.settings.SettingsManager
import com.aggin.carcost.presentation.navigation.Screen
import kotlinx.coroutines.launch

// Цвета в стиле мокапа
private val BackgroundDark = Color(0xFF0D1117)
private val CardDark = Color(0xFF161B22)
private val Green = Color(0xFF4CAF50)
private val GreenDim = Color(0xFF2E7D32)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextSecondary = Color(0xFF8B949E)

data class OnboardingPage(
    val icon: ImageVector,
    val iconTint: Color,
    val iconBackground: Color,
    val title: String,
    val subtitle: String,
    val description: String
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Default.DirectionsCar,
        iconTint = Green,
        iconBackground = Color(0xFF1A2F1A),
        title = "CarCost",
        subtitle = "Ваш автомобиль\nпод контролем",
        description = "Полный учёт расходов, история обслуживания и аналитика — всё в одном месте"
    ),
    OnboardingPage(
        icon = Icons.Default.Receipt,
        iconTint = Color(0xFF64B5F6),
        iconBackground = Color(0xFF0D1F2D),
        title = "Расходы",
        subtitle = "Учитывайте\nкаждую трату",
        description = "Записывайте топливо, ТО, штрафы, страховку и всё остальное. Прикрепляйте фото чеков прямо с камеры"
    ),
    OnboardingPage(
        icon = Icons.Default.LocalGasStation,
        iconTint = Color(0xFFFFB74D),
        iconBackground = Color(0xFF2D1F00),
        title = "Топливо",
        subtitle = "Следите за расходами\nна топливо",
        description = "Автоматический расчёт л/100км, графики потребления и расчётный остаток в баке"
    ),
    OnboardingPage(
        icon = Icons.Default.BarChart,
        iconTint = Green,
        iconBackground = Color(0xFF1A2F1A),
        title = "Аналитика",
        subtitle = "Анализируйте\nи экономьте",
        description = "Графики расходов по категориям, бюджеты на месяц, TCO и сравнение нескольких автомобилей"
    ),
    OnboardingPage(
        icon = Icons.Default.Notifications,
        iconTint = Color(0xFFEF5350),
        iconBackground = Color(0xFF2D0D0D),
        title = "Уведомления",
        subtitle = "Не пропускайте\nважное",
        description = "Push-уведомления о плановом ТО, истекающих документах и низком уровне топлива"
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(navController: NavController) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { pages.size })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                OnboardingPageContent(page = pages[page])
            }

            // Индикаторы + кнопка
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Точки-индикаторы
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pages.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 24.dp else 8.dp,
                            animationSpec = tween(300), label = "dot"
                        )
                        val color by animateColorAsState(
                            targetValue = if (isSelected) Green else TextSecondary.copy(alpha = 0.4f),
                            animationSpec = tween(300), label = "dotColor"
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                val isLastPage = pagerState.currentPage == pages.lastIndex

                // Основная кнопка
                Button(
                    onClick = {
                        if (isLastPage) {
                            scope.launch {
                                settingsManager.setOnboardingDone()
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                                }
                            }
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Green,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        if (isLastPage) "Начать" else "Далее",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Пропустить
                if (!isLastPage) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            scope.launch {
                                settingsManager.setOnboardingDone()
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                                }
                            }
                        }
                    ) {
                        Text("Пропустить", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Иконка в круглом контейнере
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            page.iconBackground,
                            BackgroundDark
                        )
                    )
                )
                .background(page.iconBackground),
            contentAlignment = Alignment.Center
        ) {
            // Внешнее свечение
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(page.iconTint.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(page.iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = page.icon,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = page.iconTint
                    )
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        // Заголовок раздела
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = page.iconTint.copy(alpha = 0.15f)
        ) {
            Text(
                text = page.title,
                color = page.iconTint,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        // Основной заголовок
        Text(
            text = page.subtitle,
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp
        )

        Spacer(Modifier.height(20.dp))

        // Описание
        Text(
            text = page.description,
            color = TextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}
