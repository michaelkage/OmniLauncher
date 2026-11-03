# Omni Launcher

**A modular, declarative Android launcher engine.**

Omni separates its native runtime from the interface it renders. Versioned JSON presets describe the launcher surface; a Kotlin/Jetpack Compose component registry renders supported components.

## Principles

- Native Kotlin + Jetpack Compose rendering.
- Material You dynamic colour on Android 12+, with selectable light/dark, Ocean, Forest, and Plum appearance palettes.
- Declarative, versioned preset documents.
- One schema shared by visual and code editors.
- Presets are data and assets, never arbitrary executable scripts.
- Standard Android operation works without Shizuku or root.
- Shizuku and root (Magisk/SU) are optional, independent capability providers.
- Explicit capability checks and user consent before privileged operations.

## Optional access backends

| Mode | Requirement | Intended role |
| --- | --- | --- |
| Standard | None | Normal launcher features and public Android APIs |
| Shizuku | Shizuku service + user authorization | Supported privileged operations through a Shizuku user service |
| Root | Root manager such as Magisk + explicit grant | Optional operations that genuinely require root |

Neither elevated backend is required to launch apps, render presets, switch layouts, or use the launcher. Root is never invoked silently. The current prototype includes a user-triggered SU identity probe and Shizuku installation detection; privileged operation adapters are still being developed.

## Architecture

- HOME activity and native Compose renderer
- Typed preset model, validator, and bundled preset repository
- Component registry: clock, grid, list, dock, and stack
- Searchable app grids and lists (matches app labels and package names)
- Theme manager for importing, previewing, applying, exporting, and removing validated JSON theme packs
- Strict declarative theme-pack schema, codec, and bounded private-storage repository with safe identifiers and validated colour tokens
- Reactive app catalogue refreshed on package add/remove/change broadcasts
- Live bundled-preset switching
- Visual layout editor for adding/removing/reordering components at the root and inside nested stacks, with app-label, grid-column, spacing, and visible-app controls; changes are validated and saved privately
- Persisted appearance preferences with wallpaper-derived Material You colours where supported
- Persistent active-preset selection with distinct bundled and saved-copy entries, plus private storage for imported presets
- Bounded .omni archive import/export through Android's document picker
- Unit tests for archive safety and app-search matching
- Optional privilege capability layer

## Getting started

Open in Android Studio with JDK 17 and Android SDK 36. Sync Gradle and run the app configuration on a device or emulator. Select **Omni Launcher** as the default Home app in Android settings.

## Presets

Bundled presets are under app/src/main/assets/presets/. Imported presets are stored in the app's private files directory and appear separately from bundled presets, even when they share a preset ID. Saving edits creates/updates the saved copy without replacing the bundled entry. Use **Import** to select a .omni archive and **Export** to write the active preset. The archive currently contains exactly one layout.json; scripts and extra archive entries are rejected. See docs/PRESET_FORMAT.md.

## Roadmap

- [x] Native HOME activity and app launching
- [x] Versioned JSON model and validation
- [x] Component registry with grid, list, dock, stack and clock
- [x] Live switching between bundled presets
- [x] Reactive clock and package-change app catalogue
- [x] Optional root capability probe and Shizuku presence detection
- [x] Persist active preset and import/export bounded declarative .omni archives
- [x] Material You dynamic colour and selectable appearance palettes
- [x] In-app layout controls for app labels, grid columns, and visible app limits
- [ ] Shizuku binder/user-service integration
- [ ] Reviewed root/Shizuku operation adapters and capability policy
- [x] Initial data-only theme-pack format and validation codec
- [x] Theme-pack import, local library, apply, and remove UI
- [ ] Validated add-on catalogue for appearance, icon, font, and component packs with explicit permissions and resource limits
- [ ] Asset-bearing .omni bundles with safe image resource handling
- [ ] Visual and code editors sharing the same document model
- [ ] Community preset catalogue and CI schema validation

## License

Omni Launcher is licensed under the **GNU General Public License v3.0 only**. See LICENSE.
