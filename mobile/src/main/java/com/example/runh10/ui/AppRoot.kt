package com.example.runh10.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.runh10.ui.components.PulseLogo
import com.example.runh10.ui.detail.RunDetailScreen
import com.example.runh10.ui.feed.FeedScreen
import com.example.runh10.ui.profile.ProfileScreen
import com.example.runh10.ui.record.RecordScreen
import com.example.runh10.ui.settings.RestingHrScreen
import com.example.runh10.ui.settings.SettingsScreen
import com.example.runh10.ui.theme.Heat
import com.example.runh10.ui.trends.TrendsScreen
import com.example.runh10.ui.watchtab.WatchTabScreen

/** Phone app shell: bottom nav (home / trends / FAB / watch / profile) + NavHost. */
@Composable
fun AppRoot(vm: SyncViewModel, onRequestPermissions: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val tabRoutes = setOf("home", "trends", "watch", "profile")

    Scaffold(
        containerColor = Heat.bg,
        bottomBar = {
            if (route in tabRoutes) {
                HeatBottomBar(
                    current = route ?: "home",
                    onTab = { target ->
                        nav.navigate(target) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onRecord = { nav.navigate("record") },
                )
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.fillMaxSize()) {
            composable("home") {
                FeedScreen(
                    onRunClick = { nav.navigate("run/$it") },
                    onProfileClick = { nav.navigate("profile") },
                    bottomPadding = padding,
                )
            }
            composable("trends") { TrendsScreen(padding) }
            composable("watch") { WatchTabScreen(vm, onRequestPermissions, padding) }
            composable("profile") {
                ProfileScreen(onEditHeart = { nav.navigate("settings") }, bottomPadding = padding)
            }
            composable("settings") {
                SettingsScreen(
                    onMeasureResting = { nav.navigate("resting") },
                    onBack = { nav.popBackStack() },
                    syncedAgoMs = null,
                    bottomPadding = PaddingValues(0.dp),
                )
            }
            composable("resting") { RestingHrScreen(onBack = { nav.popBackStack() }) }
            composable("record") {
                RecordScreen(
                    onClose = { nav.popBackStack() },
                    onSaved = { id ->
                        nav.navigate("run/$id") {
                            popUpTo("home")
                        }
                    },
                )
            }
            composable("run/{id}") { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                RunDetailScreen(id, onBack = { nav.popBackStack() })
            }
        }
    }
}

@Composable
private fun HeatBottomBar(current: String, onTab: (String) -> Unit, onRecord: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(78.dp)
            .background(Color(0xEB0A0C0F)),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Heat.hairline))
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavSlot(active = current == "home", onClick = { onTab("home") }) { c -> PulseLogo(24.dp, color = c) }
            NavSlot(active = current == "trends", onClick = { onTab("trends") }) { c -> BarsGlyph(24.dp, c) }
            // Center FAB
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .offset(y = (-22).dp)
                        .size(56.dp)
                        .background(Heat.brandGradient, CircleShape)
                        .clickable(onClick = onRecord),
                    contentAlignment = Alignment.Center,
                ) {
                    PulseLogo(26.dp, color = Color.White, strokeWidth = 2.6.dp)
                }
            }
            NavSlot(active = current == "watch", onClick = { onTab("watch") }) { c -> WatchGlyph(24.dp, c) }
            NavSlot(active = current == "profile", onClick = { onTab("profile") }) { c -> PersonGlyph(24.dp, c) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavSlot(
    active: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    Box(
        Modifier
            .weight(1f)
            .fillMaxSize()
            .clickable(onClick = onClick)
            .padding(bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        icon(if (active) Heat.brandRed else Heat.textFaint)
    }
}

@Composable
private fun BarsGlyph(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val s = Stroke(width = w * 0.1f, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.2f, h * 0.85f), Offset(w * 0.2f, h * 0.45f), strokeWidth = s.width, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, h * 0.85f), Offset(w * 0.5f, h * 0.15f), strokeWidth = s.width, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.8f, h * 0.85f), Offset(w * 0.8f, h * 0.55f), strokeWidth = s.width, cap = StrokeCap.Round)
    }
}

@Composable
private fun WatchGlyph(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val s = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        drawRoundRect(
            color,
            topLeft = Offset(w * 0.25f, h * 0.25f),
            size = androidx.compose.ui.geometry.Size(w * 0.5f, h * 0.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.15f),
            style = s,
        )
        drawLine(color, Offset(w * 0.38f, h * 0.12f), Offset(w * 0.62f, h * 0.12f), strokeWidth = s.width, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.38f, h * 0.88f), Offset(w * 0.62f, h * 0.88f), strokeWidth = s.width, cap = StrokeCap.Round)
    }
}

@Composable
private fun PersonGlyph(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val s = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        drawCircle(color, radius = w * 0.16f, center = Offset(w / 2f, h * 0.3f), style = s)
        val p = Path().apply {
            moveTo(w * 0.2f, h * 0.85f)
            cubicTo(w * 0.2f, h * 0.58f, w * 0.8f, h * 0.58f, w * 0.8f, h * 0.85f)
        }
        drawPath(p, color, style = s)
    }
}
