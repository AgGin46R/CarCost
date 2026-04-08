package com.aggin.carcost.presentation.screens.ai_insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.entities.AiInsight
import com.aggin.carcost.data.local.database.entities.InsightSeverity
import com.aggin.carcost.data.local.database.entities.InsightType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiInsightsScreen(
    carId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val viewModel: AiInsightsViewModel = viewModel(
        factory = AiInsightsViewModelFactory(
            application = context.applicationContext as android.app.Application,
            carId = carId
        )
    )
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI-советы") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                        }
                    }
                    if (uiState.unreadCount > 0) {
                        TextButton(onClick = { viewModel.markAllRead() }) {
                            Text("Прочитать все")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (uiState.insights.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Нет советов",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Добавьте больше расходов,\nчтобы получить персональные советы",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.isRefreshing) {
                item {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
            items(uiState.insights, key = { it.id }) { insight ->
                InsightCard(
                    insight = insight,
                    onRead = { viewModel.markAsRead(insight.id) }
                )
            }
        }
    }
}

@Composable
private fun InsightCard(
    insight: AiInsight,
    onRead: () -> Unit
) {
    val severityColor = when (insight.severity) {
        InsightSeverity.CRITICAL -> Color(0xFFEF5350)
        InsightSeverity.WARNING -> Color(0xFFFFB74D)
        InsightSeverity.INFO -> MaterialTheme.colorScheme.primary
    }

    val bgColor = when (insight.severity) {
        InsightSeverity.CRITICAL -> Color(0xFF2D0D0D)
        InsightSeverity.WARNING -> Color(0xFF2D1F00)
        InsightSeverity.INFO -> MaterialTheme.colorScheme.surfaceVariant
    }

    val typeIcon = when (insight.type) {
        InsightType.ANOMALY, InsightType.COST_SPIKE -> Icons.Default.TrendingUp
        InsightType.SEASONAL_TIP -> Icons.Default.WbSunny
        InsightType.BUDGET_ALERT -> Icons.Default.Warning
        InsightType.MAINTENANCE_PREDICTION -> Icons.Default.Build
        InsightType.FUEL_EFFICIENCY -> Icons.Default.LocalGasStation
        InsightType.SAVINGS_OPPORTUNITY -> Icons.Default.Savings
        InsightType.GENERAL -> Icons.Default.Info
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        onClick = { if (!insight.isRead) onRead() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(severityColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = typeIcon,
                    contentDescription = null,
                    tint = severityColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = insight.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (!insight.isRead) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(severityColor, shape = androidx.compose.foundation.shape.CircleShape)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = insight.body,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
