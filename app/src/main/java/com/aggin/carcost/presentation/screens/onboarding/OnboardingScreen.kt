package com.aggin.carcost.presentation.screens.onboarding

import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.aggin.carcost.data.demo.DemoDataSeeder
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.settings.SettingsManager
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.presentation.navigation.Screen
import kotlinx.coroutines.launch
import com.aggin.carcost.presentation.navigation.navigateOnce
import androidx.compose.runtime.saveable.rememberSaveable

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

/**
 * Страницы приветствия.
 *
 * Функция, а не свойство файла: тексты читаются из ресурсов, а те доступны
 * только внутри композиции. Свойство вычислилось бы один раз при загрузке
 * класса — до того, как язык вообще известен.
 */
@Composable
private fun onboardingPages(): List<OnboardingPage> = listOf(
    OnboardingPage(
        icon = Icons.Default.DirectionsCar,
        iconTint = Green,
        iconBackground = Color(0xFF1A2F1A),
        title = "CarCost",
        subtitle = stringResource(R.string.onboarding_vash_avtomobil_npod_kontrolem),
        description = stringResource(R.string.onboarding_polnyy_uchet_rashodov_istoriya)
    ),
    OnboardingPage(
        icon = Icons.Default.Receipt,
        iconTint = Color(0xFF64B5F6),
        iconBackground = Color(0xFF0D1F2D),
        title = stringResource(R.string.onboarding_rashody),
        subtitle = stringResource(R.string.onboarding_uchityvayte_nkazhduyu_tratu),
        description = stringResource(R.string.onboarding_zapisyvayte_toplivo_to_shtrafy_strahovku)
    ),
    OnboardingPage(
        icon = Icons.Default.LocalGasStation,
        iconTint = Color(0xFFFFB74D),
        iconBackground = Color(0xFF2D1F00),
        title = stringResource(R.string.home_toplivo),
        subtitle = stringResource(R.string.onboarding_sledite_za_rashodami_nna_toplivo),
        description = stringResource(R.string.onboarding_avtomaticheskiy_raschet_l_100km_grafiki)
    ),
    OnboardingPage(
        icon = Icons.Default.BarChart,
        iconTint = Green,
        iconBackground = Color(0xFF1A2F1A),
        title = stringResource(R.string.cardetail_analitika),
        subtitle = stringResource(R.string.onboarding_analiziruyte_ni_ekonomte),
        description = stringResource(R.string.onboarding_grafiki_rashodov_po_kategoriyam_byudzhety)
    ),
    OnboardingPage(
        icon = Icons.Default.Notifications,
        iconTint = Color(0xFFEF5350),
        iconBackground = Color(0xFF2D0D0D),
        title = stringResource(R.string.onboarding_uvedomleniya),
        subtitle = stringResource(R.string.onboarding_ne_propuskayte_nvazhnoe),
        description = stringResource(R.string.onboarding_push_uvedomleniya_o_planovom_to)
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(navController: NavController) {
    val pages = onboardingPages()
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { pages.size })
    var loadDemoData by rememberSaveable { mutableStateOf(false) }
    var isSeedingDemo by rememberSaveable { mutableStateOf(false) }

    // Request POST_NOTIFICATIONS when the user reaches the last (Notifications) page
    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — proceed either way */ }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == pages.lastIndex &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            val already = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!already) {
                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

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

                // Demo data toggle (only on last page)
                if (isLastPage) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = loadDemoData,
                            onCheckedChange = { loadDemoData = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Green,
                                uncheckedColor = TextSecondary
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.onboarding_zagruzit_demo_dannye_toyota_camry_s),
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Основная кнопка
                Button(
                    onClick = {
                        if (isLastPage) {
                            scope.launch {
                                if (loadDemoData) {
                                    isSeedingDemo = true
                                    try {
                                        val db = AppDatabase.getDatabase(context)
                                        val userId = SupabaseAuthRepository().getUserId() ?: "demo"
                                        DemoDataSeeder.seed(db, userId)
                                    } catch (_: Exception) { }
                                    isSeedingDemo = false
                                }
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
                    enabled = !isSeedingDemo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Green,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isSeedingDemo) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            if (isLastPage) stringResource(R.string.onboarding_nachat) else stringResource(R.string.action_next),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
                        Text(stringResource(R.string.action_skip), color = TextSecondary, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    // Содержимое страницы фиксированное и на обычном экране помещается, но при
    // крупном системном шрифте заголовок с описанием разрастаются вдвое. Column
    // без прокрутки в такой ситуации не уводит лишнее вниз, а сплющивает нижние
    // элементы — текст превратился бы в обрезанную полоску.
    //
    // Одной verticalScroll мало: внутри прокрутки высота не ограничена, и
    // Arrangement.Center перестаёт центрировать — страница уехала бы к верхнему
    // краю. Поэтому heightIn(min = maxHeight): содержимое занимает минимум экран,
    // центрируется как раньше, а когда перерастает — начинает прокручиваться.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = screenHeight)
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
}
