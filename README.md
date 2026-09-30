# Android Emulator Launcher

A lightweight macOS desktop app for discovering and launching existing Android Virtual Devices (AVDs) without opening Android Studio or typing emulator commands in a terminal.

It is built with Kotlin Multiplatform and Compose Desktop, with an Android Studio-inspired dark interface.

## What it does

- Automatically finds the Android SDK on macOS.
- Lists the AVDs already configured on your machine.
- Starts an emulator with one click.
- Creates new AVDs from Android device profiles and installed system images.
- Shows loading, empty, running, and error states.
- Refreshes the emulator list at any time.

> This app launches existing emulators. Android SDK command-line tools and at least one AVD must already be installed; the app does not create AVDs or install the SDK.

## Install on macOS

1. Download the Apple Silicon (`arm64`) DMG from the project's GitHub Releases page.
2. Open the DMG.
3. Drag **Android Emulator Launcher.app** onto the **Applications** shortcut.
4. Open **Android Emulator Launcher** from Applications.

If macOS blocks the first launch, Control-click the app, choose **Open**, then confirm **Open** in the dialog.

## Requirements

- macOS on Apple Silicon (M1, M2, M3, or later) for the current `arm64` build.
- Android SDK with the Emulator package and Command-line Tools installed.
- At least one configured Android Virtual Device (AVD).

The app looks for the SDK in this order:

1. `ANDROID_HOME`
2. `ANDROID_SDK_ROOT`
3. `~/Library/Android/sdk` (the usual macOS SDK location)
4. `~/Android/Sdk`

## Usage

1. Start the app.
2. Wait for it to load your AVDs.
3. Click **Launch** next to the emulator you want to run.
4. Use **Refresh** after creating or deleting an AVD.

To create a new emulator, click **Create Emulator**, select a device profile and installed Android system image, confirm or edit the suggested name, then click **Create Emulator**.

If no emulators appear, confirm that the Android SDK and an AVD are installed, then make sure one of the SDK locations above is available.

## Develop locally

```bash
./gradlew :desktopApp:run
```

To generate a macOS DMG on an Apple Silicon Mac:

```bash
./gradlew :desktopApp:packageDmg
```

The generated application and distribution files are placed under `desktopApp/build/compose/binaries/`.

## Tech stack

- Kotlin Multiplatform
- Compose Multiplatform / Compose Desktop
- Material 3
- Gradle

## Project structure

```text
desktopApp/   Desktop application entry point and macOS resources
shared/       UI, AVD discovery, SDK location, and emulator-launch logic
```

## License

Add a license file before publishing if you want others to reuse or contribute to this project.
