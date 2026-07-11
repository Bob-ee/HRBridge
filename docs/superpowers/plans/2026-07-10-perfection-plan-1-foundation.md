# Perfection Pass — Plan 1: Foundation (Phases 0–1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish the v2 baseline on master and on both devices, fix the phone insets defect, extract the GPS-cutout diagnosis from on-watch data, and produce the visual-audit + reliability findings that seed Plan 2.

**Architecture:** No new components. Merges the code-complete `redesign-heat` branch to master, applies surgical Compose insets fixes in `:mobile`, and runs two evidence-gathering efforts (NDJSON breadcrumb analysis, ADB screenshot audit) whose written outputs are the inputs to Plan 2.

**Tech Stack:** Kotlin, Jetpack Compose (M3 phone / Wear Compose watch), Gradle, adb.

## Global Constraints

- `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` before any `./gradlew` command. Repo: `/Users/bobbywhiteley/AndroidStudioProjects/RunH10`.
- adb: `~/Library/Android/sdk/platform-tools/adb` (also at `/opt/homebrew/bin/adb`).
- Phone installs MUST use `--user 0`: `adb -s 47041FDAP005ZG install -r --user 0 <apk>`. Never bare `adb install` to the phone; the app must never appear in Private space (user 10). Verify after every phone install: `adb -s 47041FDAP005ZG shell dumpsys package com.example.runh10 | grep -A1 "User 10:"` → must show `installed=false`.
- Commits authored as `Bob-ee <robertjwhiteley@gmail.com>`, no AI attribution of any kind (no Co-Authored-By, no "Generated with"). Use `git -c user.name="Bob-ee" -c user.email="robertjwhiteley@gmail.com" commit ...`.
- Watch = Pixel Watch 3 over wireless ADB at 192.168.0.120 (port varies; discover via `adb mdns services`). If unauthorized, Bob must re-pair — do not loop retrying.
- Package `com.example.runh10`; display name "HR Bridge". Never change either.
- Do not modify `.idea/*`; leave the two dirty `.idea` files out of all commits.

---

### Task 1: Merge redesign-heat to master and push

**Files:**
- Modify: none (git only)

**Interfaces:**
- Produces: `master` == `redesign-heat` (HEAD includes spec commit `9d4b2ca`), pushed to `origin/main` and `origin/redesign-heat`. All later tasks branch from this master.

- [ ] **Step 1: Confirm clean tree and merge**

```bash
cd /Users/bobbywhiteley/AndroidStudioProjects/RunH10
git status --porcelain          # expect ONLY the two .idea/ modified lines
git checkout master
git merge redesign-heat         # expect "Fast-forward" (redesign-heat is ahead of master)
```
If the merge is not a fast-forward, STOP and report — do not resolve conflicts ad hoc.

- [ ] **Step 2: Verify build + tests green on merged master**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :shared:test :wear:testDebugUnitTest :mobile:testDebugUnitTest :wear:assembleDebug :mobile:assembleDebug
```
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Push**

```bash
git push origin master:main
git push origin redesign-heat
git log --oneline -3            # expect 9d4b2ca (spec) at or near HEAD
```

### Task 2: Fix status-bar insets on Settings, Resting-HR, Record loop, Run Detail

**Files:**
- Modify: `mobile/src/main/java/com/example/runh10/ui/AppRoot.kt:88`
- Modify: `mobile/src/main/java/com/example/runh10/ui/settings/RestingHrScreen.kt` (~line 91 root Column)
- Modify: `mobile/src/main/java/com/example/runh10/ui/record/RecordScreens.kt` (root Columns of `ReadyContent` ~line 120, `LiveContent` ~line 341, `SaveContent` ~line 518)
- Modify: `mobile/src/main/java/com/example/runh10/ui/detail/RunDetailScreen.kt` (~line 77 overlay Row)

**Interfaces:**
- Consumes: `AppRoot`'s Scaffold `padding` (already includes the status-bar top inset because the Scaffold has no topBar).
- Produces: no API changes; `SettingsScreen`'s existing `bottomPadding: PaddingValues` parameter now receives the real scaffold padding.

- [ ] **Step 1: AppRoot — hand Settings the real padding**

In `AppRoot.kt`, the settings composable currently passes an explicit zero:
```kotlin
composable("settings") {
    SettingsScreen(
        onMeasureResting = { nav.navigate("resting") },
        onBack = { nav.popBackStack() },
        syncedAgoMs = null,
        bottomPadding = PaddingValues(0.dp),
    )
}
```
Change `bottomPadding = PaddingValues(0.dp)` → `bottomPadding = padding`. (SettingsScreen already does `.padding(bottomPadding)` on its root Column, so this alone fixes Settings top and bottom.) Remove the now-unused `PaddingValues`/`dp` imports only if nothing else in the file uses them (`dp` is used elsewhere — keep it).

- [ ] **Step 2: RestingHrScreen — statusBarsPadding on the root Column**

Root Column currently:
```kotlin
Column(
    Modifier.fillMaxSize().background(Heat.bg).padding(horizontal = 30.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
```
Change the modifier chain to:
```kotlin
Modifier.fillMaxSize().background(Heat.bg).statusBarsPadding().padding(horizontal = 30.dp),
```
(`statusBarsPadding()` AFTER `background` so the bar area stays painted `Heat.bg` — no white strip.) Add import `androidx.compose.foundation.layout.statusBarsPadding`.

- [ ] **Step 3: RecordScreens — statusBarsPadding on all three content roots**

`ReadyContent`'s root Column currently:
```kotlin
Column(
    Modifier
        .fillMaxSize()
        .background(Heat.bg)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp),
) {
```
Insert `.statusBarsPadding()` immediately after `.background(Heat.bg)` (before scroll, so the content scrolls under nothing). Apply the same insertion to the root Column/Box of `LiveContent` and `SaveContent` — read each function's opening modifier chain and insert `.statusBarsPadding()` directly after its `.background(...)` call. Add the import as in Step 2.

- [ ] **Step 4: RunDetailScreen — clear the clock without losing full-bleed**

The map hero Box stays edge-to-edge. Its overlay controls Row currently:
```kotlin
Row(
    Modifier.fillMaxWidth().padding(top = 18.dp, start = 20.dp, end = 20.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
) {
```
Change to:
```kotlin
Row(
    Modifier.fillMaxWidth().statusBarsPadding().padding(top = 6.dp, start = 20.dp, end = 20.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
) {
```
Add the import as above.

- [ ] **Step 5: Build**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :mobile:assembleDebug
```
Expected: `BUILD SUCCESSFUL`. (Visual verification happens on-device in Task 4/5 screenshots — insets aren't unit-testable.)

- [ ] **Step 6: Commit**

```bash
git add mobile/src/main/java/com/example/runh10/ui/AppRoot.kt mobile/src/main/java/com/example/runh10/ui/settings/RestingHrScreen.kt mobile/src/main/java/com/example/runh10/ui/record/RecordScreens.kt mobile/src/main/java/com/example/runh10/ui/detail/RunDetailScreen.kt
git -c user.name="Bob-ee" -c user.email="robertjwhiteley@gmail.com" commit -m "fix(mobile): status-bar insets on settings, resting-HR, record loop, run detail"
```

### Task 3: Pull watch sessions and diagnose the GPS cutout from EvtRow breadcrumbs

**Files:**
- Create: `docs/superpowers/plans/notes/2026-07-10-gps-cutout-diagnosis.md` (the deliverable — a written root-cause finding)

**Interfaces:**
- Consumes: wireless-ADB-paired watch (Bob's pairing); on-watch NDJSON at `files/sessions/*.ndjson` in the app's private dir.
- Produces: diagnosis doc with (a) per-session GPS timeline table, (b) EvtRow transition sequences, (c) root-cause statement with evidence, or an explicit "needs one instrumented run because X". Plan 2's GPS-fix task is authored from this doc.

- [ ] **Step 1: Connect to the watch**

```bash
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB mdns services                       # find 192.168.0.120:<port> (_adb-tls-connect._tcp)
$ADB connect 192.168.0.120:<port>
$ADB devices -l                          # expect the watch (model sol) listed as "device"
```
If `failed to connect`, the pairing is stale → ask Bob to pair (Settings → Developer options → Wireless debugging → Pair new device) and STOP this task until then; other tasks proceed.

- [ ] **Step 2: Pull every session file**

```bash
WATCH=192.168.0.120:<port>
mkdir -p /private/tmp/claude-501/-Users-bobbywhiteley-Documents-Claude-Projects-WatchApp/47bed566-0ba0-4220-819b-bb0628f9c2a3/scratchpad/watch-sessions
cd $_
for f in $($ADB -s $WATCH shell run-as com.example.runh10 ls files/sessions); do
  $ADB -s $WATCH shell run-as com.example.runh10 cat files/sessions/$f > $f
done
ls -la    # expect the known 6/30+ runs; sizes 100KB–400KB each
```

- [ ] **Step 3: Analyze GPS behavior per session**

For each `.ndjson` (jq is available; rows have a `t` type discriminator — `loc`, `hr`, `rr`, `evt`):
```bash
for f in *.ndjson; do
  echo "== $f"
  jq -rs '[.[] | select(.t=="loc")] | "loc rows: \(length)  first: \(first.ts)  last: \(last.ts)"' $f 2>/dev/null
  jq -c 'select(.t=="evt")' $f
done
```
Build a per-run timeline: when did loc rows stop relative to run start; what EvtRow GPS-state / exercise-state transitions bracket the cutout; did GPS state ever report re-acquisition; HR continuity through the same window (rules out app death). Check whether cutouts correlate with screen-off/ambient (EvtRow state events), a fixed ~45 s offset, or Health Services exercise-state changes.

- [ ] **Step 4: Cross-check candidate causes against evidence**

Evaluate at minimum: (a) missing `ACCESS_BACKGROUND_LOCATION` (prior hypothesis), (b) Health Services location-availability transition never recovering after first doze, (c) exercise config or FGS-type issue (`startAsForeground()` is called twice per run — known oddity), (d) battery-saver/location-throttle on the watch. The diagnosis must explain BOTH the ~45 s onset AND the permanent non-recovery. Cite specific EvtRow lines.

- [ ] **Step 5: Write and commit the diagnosis doc**

Write `docs/superpowers/plans/notes/2026-07-10-gps-cutout-diagnosis.md` with the timeline tables, the surviving root cause (or the explicit instrumented-run ask), and the recommended fix shape. Commit:
```bash
git add docs/superpowers/plans/notes/2026-07-10-gps-cutout-diagnosis.md
git -c user.name="Bob-ee" -c user.email="robertjwhiteley@gmail.com" commit -m "docs: GPS cutout root-cause diagnosis from EvtRow breadcrumbs"
```

### Task 4: Version-stamp and install v2 on both devices

**Files:**
- Modify: `mobile/build.gradle.kts` (versionName), `wear/build.gradle.kts` (versionName)

**Interfaces:**
- Consumes: Task 1 (master baseline), Task 2 (insets fix), watch connection from Task 3 Step 1.
- Produces: both devices running v2 `versionName "2.0"`; phone clean of user-10 install.

- [ ] **Step 1: Bump versionName to 2.0 in both app modules**

In `mobile/build.gradle.kts` and `wear/build.gradle.kts`, find `versionName = "1.0"` (or `versionName "1.0"`) in `defaultConfig` and change to `"2.0"`. Bump `versionCode` by 1 in each.

- [ ] **Step 2: Build both APKs**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :wear:assembleDebug :mobile:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Install — phone with --user 0, watch normally**

```bash
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB -s 47041FDAP005ZG install -r --user 0 mobile/build/outputs/apk/debug/mobile-debug.apk
$ADB -s 192.168.0.120:<port> install -r wear/build/outputs/apk/debug/wear-debug.apk
```
Expected: `Success` twice.

- [ ] **Step 4: Verify versions, icon, and no private-space install**

```bash
$ADB -s 47041FDAP005ZG shell dumpsys package com.example.runh10 | grep versionName   # expect 2.0
$ADB -s 192.168.0.120:<port> shell dumpsys package com.example.runh10 | grep versionName  # expect 2.0
$ADB -s 47041FDAP005ZG shell dumpsys package com.example.runh10 | grep -A1 "User 10:"     # expect installed=false
```
Launch the watch app and screenshot (`$ADB -s <watch> shell screencap -p /sdcard/s.png && $ADB -s <watch> pull /sdcard/s.png`) — confirm the HEAT home screen (HR hero), not the v1 UI, and the pulse-mark launcher icon in the watch app list.

- [ ] **Step 5: Commit the version bump**

```bash
git add mobile/build.gradle.kts wear/build.gradle.kts
git -c user.name="Bob-ee" -c user.email="robertjwhiteley@gmail.com" commit -m "chore: version 2.0"
```

### Task 5: ADB screenshot audit of every screen vs the design mocks

**Files:**
- Create: `docs/superpowers/plans/notes/2026-07-10-visual-audit.md` (findings list — input to Plan 2)
- Create: screenshots under the scratchpad dir (not committed)

**Interfaces:**
- Consumes: Task 4 installs. Mocks: `/Users/bobbywhiteley/Documents/Claude/Projects/WatchApp/design/` and `"HRBridge companion app design.zip"` (unzip to scratchpad).
- Produces: numbered findings (screen, what deviates from mock or looks broken, severity P1 visual-bug / P2 deviation / P3 nit). Insets verification for Task 2 happens here: Settings, Resting-HR, Ready/Live/Save, Run Detail screenshots must show no clock/status-bar collision.

- [ ] **Step 1: Drive and capture the phone** — for each screen (Feed, Run Detail of a real run, Trends, Watch tab, Profile, Settings, Resting-HR, Record Ready — Live/Save only if reachable without a real strap session): navigate via `adb shell input tap` (get coordinates from `adb shell uiautomator dump` if needed), `adb -s 47041FDAP005ZG shell screencap -p /sdcard/x.png`, pull to scratchpad, view the image.
- [ ] **Step 2: Drive and capture the watch** — home/ready, settings pages, music screen, summary (from a stored session if reachable), tile preview (`adb shell am broadcast` won't render tiles — capture from the tiles carousel via swipes), ambient if capturable.
- [ ] **Step 3: Compare each screenshot against its mock** — write one finding per deviation; explicitly confirm the five Task-2 screens have clean status bars (if not, reopen Task 2 — that's a rejection-worthy failure).
- [ ] **Step 4: Commit the audit doc**

```bash
git add docs/superpowers/plans/notes/2026-07-10-visual-audit.md
git -c user.name="Bob-ee" -c user.email="robertjwhiteley@gmail.com" commit -m "docs: v2 visual audit findings vs design mocks"
```

### Task 6: Reliability adversarial review (code-level)

**Files:**
- Create: `docs/superpowers/plans/notes/2026-07-10-reliability-findings.md`

**Interfaces:**
- Consumes: master baseline (Tasks 1–2).
- Produces: verified findings list (file:line, failure scenario, severity) covering: BLE drop during pause/lock; process death mid-run and FGS restart semantics (both apps); session-file integrity on kill (partial NDJSON line handling); location/BT permission revoked mid-run; storage-full write path; Data Layer sync interruption + resume idempotency; phone reboot with unsynced sessions; media relay when notification access is revoked mid-run. Fixes land in Plan 2 (or immediately if one-line and test-covered).

- [ ] **Step 1: Run an independent adversarial review** over `shared/`, `wear/src/main`, `mobile/src/main` focused on the scenario list above — findings must cite code paths and a concrete failure sequence, and each must be verified against the actual code (no speculative findings).
- [ ] **Step 2: Write and commit the findings doc**

```bash
git add docs/superpowers/plans/notes/2026-07-10-reliability-findings.md
git -c user.name="Bob-ee" -c user.email="robertjwhiteley@gmail.com" commit -m "docs: reliability adversarial review findings"
```

---

## Task order & parallelism

1 → 2 → 4 are serial (baseline → fix → install). Task 3 runs as soon as the watch pairs (only Step 1 blocks on Bob). Task 5 needs Task 4. Task 6 is independent after Task 1 and can run in parallel with 3/4/5.

## What seeds Plan 2

GPS fix (from Task 3's diagnosis), visual-audit fixes (Task 5), reliability fixes (Task 6), then spec Phase 2 (battery %, calories, resting-HR auto-update). Plans 3–5 follow the spec's phases 3–5.
