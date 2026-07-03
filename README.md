# HR Bridge

Strap-grade heart data for runners. HR Bridge pairs a **Polar H10** chest strap with a
Wear OS watch and an Android phone app: raw RR intervals → true HRV (RMSSD), and HR
zones from **heart-rate reserve (%HRR / Karvonen)** — not `220 − age`.

## Apps

- **Watch (Wear OS)** — swipe-nav run experience: HR hero home (bezel zone ring),
  swipe up for Metrics/Laps, right for run controls, left for music control; plus a
  quick-launch tile with a readiness card. Records GPS via Health Services and HR/RR
  over BLE GATT directly from the strap.
- **Phone** — the companion: run feed with route heatmaps, full run analysis
  (zones donut, HRV, HR curve, splits), a complete record loop (H10 + fused GPS),
  heart profile & %HRR zones, resting-HR measurement, and watch↔phone session sync
  into Health Connect.

Design language: **HEAT** — a dark instrument-cluster aesthetic where routes render
as heart-rate heatmaps and zone color is used consistently everywhere.

## Modules

- `wear/` — Wear OS app (Compose for Wear OS)
- `mobile/` — phone app (Compose / Material 3)
- `shared/` — models, NDJSON session format, sync + media protocols, zone math,
  run-logic units, BLE HR client, HEAT design tokens, fonts (Saira, OFL)

## Build

Open in Android Studio, or:

```
./gradlew :wear:assembleDebug :mobile:assembleDebug
```
