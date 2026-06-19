package com.example.runh10.voice

import com.example.runh10.data.RunSettings
import com.example.runh10.shared.Constants
import com.example.runh10.shared.model.Split
import kotlin.math.roundToInt

object MileAnnouncement {
    fun build(split: Split, zone: Int?, s: RunSettings): String {
        val parts = mutableListOf("Mile ${split.index}.")
        if (s.announceSplitTime) parts += "Split ${clockWords(split.movingDurationMs / 1000)}."
        if (s.announcePace && split.avgPaceMps > 0) {
            val secPerMile = (Constants.MILE_METERS / split.avgPaceMps).roundToInt()
            parts += "Pace ${paceWords(secPerMile.toLong())} per mile."
        }
        if (s.announceHrZone && split.avgBpm != null) {
            val z = zone?.let { ", zone $it" } ?: ""
            parts += "Average heart rate ${split.avgBpm}$z."
        }
        return parts.joinToString(" ")
    }

    private fun clockWords(totalSec: Long): String {
        val m = totalSec / 60; val sec = totalSec % 60
        return "$m minutes $sec seconds"
    }
    private fun paceWords(totalSec: Long): String {
        val m = totalSec / 60; val sec = totalSec % 60
        return "$m minutes $sec"
    }
}
