package com.example.runh10.workout

/**
 * The run-logic units and BLE sample models moved to :shared so the phone record
 * flow can reuse them (HEAT redesign). These aliases keep every existing wear
 * import/test source-compatible.
 */
typealias RunClock = com.example.runh10.shared.run.RunClock
typealias RollingPace = com.example.runh10.shared.run.RollingPace
typealias Motion = com.example.runh10.shared.run.Motion
typealias MotionClassifier = com.example.runh10.shared.run.MotionClassifier
typealias RunState = com.example.runh10.shared.run.RunState
typealias RunEvent = com.example.runh10.shared.run.RunEvent
typealias RunStateMachine = com.example.runh10.shared.run.RunStateMachine
typealias SplitTracker = com.example.runh10.shared.run.SplitTracker
typealias HrSample = com.example.runh10.shared.run.HrSample
typealias ScanDevice = com.example.runh10.shared.run.ScanDevice
