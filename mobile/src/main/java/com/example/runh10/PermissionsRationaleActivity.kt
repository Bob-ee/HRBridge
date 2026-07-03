package com.example.runh10

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Text(
                        "HR Bridge writes your recorded runs (route, heart rate, distance, pace, " +
                            "calories, elevation, and HRV/RMSSD) to Health Connect. Data is written " +
                            "only when you sync and is never shared elsewhere."
                    )
                }
            }
        }
    }
}
