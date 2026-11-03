# Omni Launcher

A modular, declarative Android launcher engine.

Omni separates the launcher runtime from the interface it renders. The native Android app reads a versioned preset document and renders supported components with Jetpack Compose. Presets are designed to be portable, inspectable, and editable in visual and code editors.

> Status: early prototype. This bootstrap establishes the Android home-screen entry point, a small JSON schema, and native rendering. Shizuku integration, visual editing, preset discovery, and .omni packaging are planned, not yet implemented.

## Principles

- Native Kotlin and Jetpack Compose rendering.
- Data-driven layouts described by layout.json.
- Visual and code editors will share one document model.
- Presets are data and assets, never arbitrary executable scripts.
- Shizuku is optional and only used for capabilities the connected service actually provides.
- Request only the package visibility and system access needed for launcher functionality.

## Architecture

- Home activity and Android HOME entry point
- Preset repository and schema validation
- JSON layout tree to Compose renderer
- Launchable-app catalogue
- Optional Shizuku integration (future)

## Getting started

Open this project in Android Studio with JDK 17 and a compatible Android SDK. Sync Gradle and run the app configuration on an Android device or emulator. Select Omni as the default Home app in Android settings to test it as your launcher.

The starter preset is in app/src/main/assets/presets/minimal-wave.json. See docs/PRESET_FORMAT.md for the initial format.

## Roadmap

- [x] Android/Compose project structure
- [x] HOME activity and launchable-app catalogue
- [x] Versioned JSON preset model and starter preset
- [x] Native clock and app-list components
- [ ] Package add/remove refresh and lifecycle-safe catalogue
- [ ] Schema validation and preset error reporting
- [ ] Optional Shizuku capability detection and service integration
- [ ] Grid, dock, widget, and gesture components
- [ ] Visual editor and code editor sharing one schema
- [ ] .omni archive importer and exporter
- [ ] Community preset catalogue and CI validation

## License

The repository currently contains an Apache License 2.0 license file. Earlier project copy mentioned GPL-3.0; these are different licenses. The existing repository license remains authoritative until the owner deliberately changes it.
