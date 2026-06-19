package com.example.runh10.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunStateMachineTest {
    private val sm = RunStateMachine()

    @Test fun warmup_opens_on_running() {
        assertEquals(RunState.WARMUP, sm.state)
        assertEquals(RunEvent.RUN_DETECTED, sm.onMotion(Motion.RUNNING))
        assertEquals(RunState.RUNNING, sm.state)
    }

    @Test fun auto_pause_resumes_on_movement() {
        sm.onMotion(Motion.RUNNING)
        assertEquals(RunEvent.AUTO_PAUSED, sm.onMotion(Motion.IDLE))
        assertEquals(RunEvent.AUTO_RESUMED, sm.onMotion(Motion.WALKING))
        assertEquals(RunState.RUNNING, sm.state)
    }

    @Test fun manual_pause_latches_through_movement() {
        sm.onMotion(Motion.RUNNING)
        assertEquals(RunEvent.MANUAL_PAUSED, sm.manualPause())
        assertNull(sm.onMotion(Motion.RUNNING))   // movement must NOT auto-resume a manual pause
        assertEquals(RunState.MANUAL_PAUSED, sm.state)
        assertEquals(RunEvent.RESUMED, sm.manualResume())
        assertEquals(RunState.RUNNING, sm.state)
    }

    @Test fun tapping_pause_while_auto_paused_converts_to_manual() {
        sm.onMotion(Motion.RUNNING); sm.onMotion(Motion.IDLE)
        assertEquals(RunEvent.MANUAL_PAUSED, sm.manualPause())
        assertNull(sm.onMotion(Motion.RUNNING))
        assertEquals(RunState.MANUAL_PAUSED, sm.state)
    }
}
