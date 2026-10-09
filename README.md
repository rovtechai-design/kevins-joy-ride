# Kevin's Joy Ride 🏍️🏔️

A Himalayan mountain traffic-riding game for Android. Ride a **Royal Enfield Himalayan 450** through two-way mountain traffic, past prayer flags, pine forests and Leh kilometre stones, as the day turns to dusk and then night.

## Features
- **Pseudo-3D chase-cam road** with curves, snow-capped parallax mountains, and a day → dusk → night cycle with working headlights and taillights
- **Two-way traffic**: hatchbacks, SUVs, "HORN OK PLEASE" goods trucks, Manali–Leh buses and auto-rickshaws. Cars change lanes with indicators.
- **Scoring for the leaderboard**: speed-based points, ×2 in the oncoming lane, ×1.5 above 140 km/h, near-miss combos up to ×10, and overtakes
- **Two modes**: Endless (one crash and you're out) and Time Attack (60 s, +time at checkpoints every 2 km)
- **Local top-10 leaderboard** for each mode
- **10 riders**: Gokul, Yaseen, Omar, Satya, Bottlesage, Pradeep, Sanjey, Kaushik, Hugo and a nameable Custom rider, each with their own look
- **Garage**: change 14 parts of the outfit and bike (helmet style and colours, visor, jacket, pattern, pants, boots, gloves, skin tone, accessory such as cape, scarf, backpack, bottle pack or panniers, and the official Himalayan paint schemes). Side and rear 3D-style preview.
- **Synthesised single-cylinder engine sound** that follows RPM through 6 gears

## Controls
| Key | Action |
|---|---|
| `W` / ↑ | Throttle |
| `S` / ↓ | Brake |
| `A` / `D` / ← → | Steer |
| `Esc` / `P` | Pause |

On a phone, use the on-screen ◀ ▶ BRAKE GAS buttons.

## Install
Download the APK from [Releases](../../releases) and open it on your Android phone (allow "install unknown apps").

## Build
```
./gradlew assembleRelease
```
Plain Java and Android Canvas only, with no third-party dependencies.
