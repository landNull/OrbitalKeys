# OrbitalKeys Prototype

A realistic, buildable prototype of the OrbitalKeys terminal-centric Android IME.

## What works in this prototype
- Custom Canvas-based InputMethodService
- Freeform key positioning (Orbital Hub + standard grids)
- Density-independent sizing (uses dp)
- Long-press any key → context menu
- Edit Mode with drag-and-drop key swapping (saves JSON to Downloads/OrbitalKeys)
- Liftoff countdown before edit mode
- Training mode with swipe trail recording and JSON export
- Terminal-friendly input (`commitText` + raw `KeyEvent`s)
- Basic speech recognition (MIC key)
- Layout switching (Orbital Hub, QWERTY, Dvorak, Colemak)
- Upper gutter status bar

## What is intentionally simplified
- No full AOSP LatinIME glide decoder (swipes are recorded only)
- Limited key sets (enough to test interaction)
- Dialogs use overlay type (requires SYSTEM_ALERT_WINDOW permission)
- No shift/caps, symbols layer, or prediction engine

## How to build and test
1. Open the `orbitalkeys` folder in Android Studio (or use command line with Android SDK).
2. Sync Gradle and build the debug APK:
   ```
   ./gradlew assembleDebug
   ```
3. Install on device:
   ```
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
4. Enable OrbitalKeys in Settings → System → Languages & input → On-screen keyboard.
5. Long-press any key to open the control menu.
6. Choose Edit Mode to drag keys. Overlapping keys swap positions and export the layout.

## Notes
- Grant "Display over other apps" if the menu dialogs fail to appear.
- Swipe trails and layout changes are written to Downloads/OrbitalKeys/.
- Designed for testing the freeform canvas + edit mode interaction model from the brief.
