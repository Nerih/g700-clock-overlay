# G700 Clock Overlay

A lightweight Android app for testing a transparent digital clock overlay on a secondary display.

This project is being built for the Jetour G700 workflow, where the app is intended to render only the clock text on an external display path that can later be routed to `HDMI:2` using LANSO. The main display acts as a control panel, while the secondary display shows the live clock output.

## Current Goals

- Render a live digital clock on a secondary display
- Keep the clock output running while configuration is changed on the main display
- Support transparent background output for overlay-style rendering
- Allow position tuning with visible X/Y values
- Save settings so they survive app restarts
- Detect and test available displays to identify the correct LANSO / HDMI:2 target

## Current Features

### Main display
- Control panel UI
- Position adjustment
- Preset position buttons
- Font size control
- 12/24 hour toggle
- Seconds on/off toggle
- Display/output selection
- Debug information for detected displays
- Persistent saved settings

### Secondary display
- Live digital clock
- Real-time updates from the control panel
- Designed for transparent background output
- Intended for external display routing / HDMI overlay testing

## Project Status

Prototype / work in progress.

The app currently focuses on proving the Android multi-display pipeline before testing on the actual G700 head unit. The emulator is used first to validate:

- app build and launch
- multi-display detection
- external display rendering
- live configuration updates
- transparent output behavior

After that, the APK will be tested on the G700 via ADB and routed through LANSO to determine which output maps correctly to `HDMI:2`.

## Technical Approach

- **Android OS target environment:** G700 head unit running Android 14
- **App target SDK:** Android 13 (`targetSdk 33`)
- **Compile SDK:** 34
- **Language:** Kotlin
- **UI:** Jetpack Compose for the main control screen
- **External display rendering:** `Presentation`-based secondary display output

## Why this app exists

Most Android overlay approaches rely on floating windows or system overlay permissions, which can be restrictive and unreliable on newer Android versions and custom vendor ROMs.

This project instead tests a cleaner model:

- main screen = control/configuration UI
- secondary display = clock output only

That makes it easier to integrate with unknown head unit display routing behavior and tools like LANSO.

## Planned Improvements

- transparent external output refinement
- better X/Y positioning model with anchors
- named preset positions
- pixel mode in addition to dp mode
- drag-to-position calibration
- optional alignment grid
- display refresh / start / stop output controls
- release APK generation
- on-device G700 testing and LANSO mapping validation

## Emulator Testing

The recommended first test environment is the Android Emulator.

### Basic flow
1. Run the app in the emulator
2. Add a secondary display in emulator extended controls
3. Refresh detected displays in the app
4. Select the non-main display as output
5. Confirm the live clock renders there
6. Adjust position and style from the main display UI

## Intended Real Device Flow

1. Build APK
2. Install to G700 over ADB
3. Launch the app on the main display
4. Use the output/debug panel to identify the correct external display
5. Route the selected display via LANSO
6. Confirm output appears on `HDMI:2`

## Notes

- This project is experimental
- Behavior may differ between emulator and the actual G700 vendor ROM
- Transparent output and external display handling may require additional tuning on the real device
- LANSO integration behavior is still being validated

## Repository Structure

Typical Android Studio project structure:

```text
app/
 ├─ src/
 │   ├─ main/
 │   │   ├─ java/com/hiren/g700clock/
 │   │   ├─ res/
 │   │   └─ AndroidManifest.xml
 │   └─ ...
 ├─ build.gradle.kts
 └─ ...