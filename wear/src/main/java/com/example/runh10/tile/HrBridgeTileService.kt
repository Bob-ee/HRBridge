package com.example.runh10.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.Layout
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Corner
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.example.runh10.data.RunHistoryPrefs
import com.example.runh10.data.SettingsStore
import com.example.runh10.presentation.MainActivity
import com.example.runh10.shared.design.HeatTokens
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Quick-launch tile — the "front door": readiness (resting HR + last-run HRV),
 * last run line, and a START RUN action that deep-links into the app.
 * Tiles can't render custom fonts or gradients; colors and hierarchy carry HEAT here.
 */
class HrBridgeTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        scope.future {
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(30 * 60 * 1000L)
                .setTileTimeline(Timeline.fromLayoutElement(buildLayout()))
                .build()
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build(),
        )

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun buildLayout(): LayoutElementBuilders.LayoutElement {
        val settings = SettingsStore(applicationContext).settings.first()
        val lastRun = RunHistoryPrefs(applicationContext).lastRun.first()

        val hrv = lastRun?.hrvMs
        val prevHrv = lastRun?.prevHrvMs
        val delta = if (hrv != null && prevHrv != null) (hrv - prevHrv).roundToInt() else null
        val recovered = delta == null || delta >= -2
        val readinessText = if (recovered) "RECOVERED" else "TAKE IT EASY"
        val readinessColor = if (recovered) HeatTokens.GOOD_GREEN else HeatTokens.Z4

        val hrvLabel = when {
            delta == null -> "HRV"
            delta >= 0 -> "HRV ↑ $delta"
            else -> "HRV ↓ ${-delta}"
        }
        val lastLine = lastRun?.let {
            "Last · ${it.name} · ${String.format(Locale.US, "%.2f", it.distanceMeters / 1609.344)} mi"
        } ?: "No runs yet"

        val startClick = Clickable.Builder()
            .setId("start_run")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(MainActivity::class.java.name)
                            .addKeyToExtraMapping(
                                MainActivity.EXTRA_TILE_START,
                                ActionBuilders.AndroidBooleanExtra.Builder().setValue(true).build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()

        return Box.Builder()
            .setWidth(androidx.wear.protolayout.DimensionBuilders.expand())
            .setHeight(androidx.wear.protolayout.DimensionBuilders.expand())
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(Background.Builder().setColor(argb(HeatTokens.BG.toInt())).build())
                    .build(),
            )
            .addContent(
                Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(text("HR BRIDGE", 14f, HeatTokens.TEXT_MUTED, bold = true))
                    .addContent(Spacer.Builder().setHeight(dp(8f)).build())
                    .addContent(readinessCard(readinessText, readinessColor, settings.restingHr, hrv, hrvLabel))
                    .addContent(Spacer.Builder().setHeight(dp(8f)).build())
                    .addContent(text(lastLine, 12f, HeatTokens.TEXT_MUTED))
                    .addContent(Spacer.Builder().setHeight(dp(10f)).build())
                    .addContent(startButton(startClick))
                    .build(),
            )
            .build()
    }

    private fun readinessCard(
        readiness: String,
        readinessColor: Long,
        restingHr: Int?,
        hrvMs: Double?,
        hrvLabel: String,
    ): LayoutElementBuilders.LayoutElement =
        Box.Builder()
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(
                        Background.Builder()
                            .setColor(argb(HeatTokens.SURFACE.toInt()))
                            .setCorner(Corner.Builder().setRadius(dp(18f)).build())
                            .build(),
                    )
                    .setPadding(
                        Padding.Builder()
                            .setStart(dp(16f)).setEnd(dp(16f)).setTop(dp(11f)).setBottom(dp(11f))
                            .build(),
                    )
                    .build(),
            )
            .addContent(
                Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(text(readiness, 13f, readinessColor, bold = true))
                    .addContent(Spacer.Builder().setHeight(dp(7f)).build())
                    .addContent(
                        Row.Builder()
                            .addContent(
                                statColumn(
                                    restingHr?.toString() ?: "—", "RESTING", HeatTokens.INFO_BLUE,
                                ),
                            )
                            .addContent(Spacer.Builder().setWidth(dp(20f)).build())
                            .addContent(
                                statColumn(
                                    hrvMs?.roundToInt()?.toString() ?: "—", hrvLabel, HeatTokens.HRV_PURPLE,
                                ),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()

    private fun statColumn(value: String, label: String, color: Long): LayoutElementBuilders.LayoutElement =
        Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text(value, 24f, color, bold = true))
            .addContent(text(label, 10f, HeatTokens.TEXT_DIM))
            .build()

    private fun startButton(click: Clickable): LayoutElementBuilders.LayoutElement =
        Box.Builder()
            .setModifiers(
                Modifiers.Builder()
                    .setClickable(click)
                    .setBackground(
                        Background.Builder()
                            .setColor(argb(HeatTokens.BRAND_RED.toInt()))
                            .setCorner(Corner.Builder().setRadius(dp(18f)).build())
                            .build(),
                    )
                    .setPadding(
                        Padding.Builder()
                            .setStart(dp(26f)).setEnd(dp(26f)).setTop(dp(11f)).setBottom(dp(11f))
                            .build(),
                    )
                    .build(),
            )
            .addContent(text("START RUN", 17f, HeatTokens.TEXT, bold = true))
            .build()

    private fun text(s: String, sizeSp: Float, color: Long, bold: Boolean = false): Text =
        Text.Builder()
            .setText(s)
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(sizeSp))
                    .setColor(argb(color.toInt()))
                    .setWeight(
                        if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD
                        else LayoutElementBuilders.FONT_WEIGHT_NORMAL,
                    )
                    .build(),
            )
            .build()

    companion object {
        private const val RESOURCES_VERSION = "1"
    }
}
