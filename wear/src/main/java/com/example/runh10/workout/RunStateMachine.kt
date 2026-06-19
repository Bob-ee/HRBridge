package com.example.runh10.workout

enum class RunState { WARMUP, RUNNING, AUTO_PAUSED, MANUAL_PAUSED }
enum class RunEvent { RUN_DETECTED, AUTO_PAUSED, AUTO_RESUMED, MANUAL_PAUSED, RESUMED }

class RunStateMachine {
    var state: RunState = RunState.WARMUP
        private set

    fun onMotion(m: Motion): RunEvent? = when (state) {
        RunState.WARMUP -> if (m == Motion.RUNNING) { state = RunState.RUNNING; RunEvent.RUN_DETECTED } else null
        RunState.RUNNING -> if (m == Motion.IDLE) { state = RunState.AUTO_PAUSED; RunEvent.AUTO_PAUSED } else null
        RunState.AUTO_PAUSED -> if (m != Motion.IDLE) { state = RunState.RUNNING; RunEvent.AUTO_RESUMED } else null
        RunState.MANUAL_PAUSED -> null   // latched
    }

    fun manualPause(): RunEvent? = when (state) {
        RunState.RUNNING, RunState.AUTO_PAUSED -> { state = RunState.MANUAL_PAUSED; RunEvent.MANUAL_PAUSED }
        else -> null
    }

    fun manualResume(): RunEvent? = when (state) {
        RunState.MANUAL_PAUSED, RunState.AUTO_PAUSED -> { state = RunState.RUNNING; RunEvent.RESUMED }
        else -> null
    }

    /** Warmup "Start now" override. */
    fun startNow(): RunEvent? =
        if (state == RunState.WARMUP) { state = RunState.RUNNING; RunEvent.RUN_DETECTED } else null
}
