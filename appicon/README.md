# Hisaab — App Icon ("Midnight")

An outline wallet mark with an AI spark, in electric lime (#CBF24A) on a deep-ink gradient
with a soft top glow. Generated at exact platform sizes. No alpha on iOS masters; Android
adaptive layers included.

## iOS  (appicon/ios/)
- `AppIcon-1024.png` is the App Store / universal icon. `Contents.json` is set up for a
  single-size universal AppIcon (Xcode 14+).
- **Drop-in**: replace `Assets.xcassets/AppIcon.appiconset/` contents with this folder,
  or in Xcode select the AppIcon set and drag `AppIcon-1024.png` onto the single well.
- Individual legacy sizes (20–180) are included if your target uses a per-size set.
- Icons are full-bleed and opaque (Apple requirement); iOS applies the rounded mask.

## Android  (appicon/android/)
Adaptive icon (API 26+), recommended:
- `mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` reference two layers:
  - `ic_launcher_foreground` (transparent, glyph in the 66% safe zone)
  - `ic_launcher_background` (gradient)
- Per-density `foreground`/`background` PNGs live in each `mipmap-*` folder.
- Legacy launchers use the full-bleed `ic_launcher.png` / `ic_launcher_round.png` per density.
- `playstore-512.png` is the Google Play listing icon.
- **Drop-in**: merge the `mipmap-*` folders into `app/src/main/res/`.

### Easiest path (either platform)
Use `master-1024.png` with Xcode's single-size AppIcon, or Android Studio →
**Image Asset Studio** (New → Image Asset → Launcher Icons) to auto-generate every
density/adaptive layer from the master.

## Brand
- Background gradient: `#181b22 → #0E1014 → #08090C`, lime radial glow @ top-center.
- Mark: outline wallet + AI spark, color `#CBF24A`, subtle lime glow.
