# Omni preset format — schema version 1

Presets are declarative JSON documents. They are data, not executable code.

## Top-level structure

- schema_version: currently 1
- preset_metadata: stable ID, name, author and version fields
- global_styles: shared visual tokens
- interaction_matrix: data-only interaction declarations
- layout_hierarchy: root layout and ordered components

## User appearance preferences

The current app-level appearance setting is stored separately from a preset so switching layouts does not unexpectedly reset the user's preferred look. Users can choose system/light/dark mode and Material You wallpaper colours (Android 12+) or the built-in Ocean, Forest, and Plum palettes. These preferences are not yet serialized into `layout.json`.

## In-app layout customization

The current customization panel can change app-label visibility, grid column count, and the maximum visible app count for supported app components. Applying changes saves a private copy of the active preset on-device; exporting the active preset shares those changes in the existing `.omni` format. Appearance preferences remain app-level settings and are not serialized into the layout.

## Declarative theme packs (schema version 1)

The initial theme-pack codec defines a separate JSON format for colour palettes. A pack contains `schema_version`, `pack_metadata` (`id`, `name`, `author`, `version`), and `palettes.light` / `palettes.dark`. Each palette currently accepts only five six-digit hex colour tokens: `primary`, `secondary`, `tertiary`, `background`, and `surface`.

The codec rejects unknown JSON fields, unsupported schema versions, unsafe IDs, invalid metadata, malformed colours, and input larger than 64 KiB. Packs are data only: no scripts, URLs, arbitrary component definitions, fonts, or images are accepted. The codec, validation tests, and private app-storage repository are implemented. The repository supports bounded import, validation, save, list, load, and remove operations. Previewing and applying packs, plus the management UI, are still future work. This format is separate from `.omni` layout archives.

## Add-on direction

Future add-ons are planned as signed/validated declarative resource packs: appearance palettes, icon metadata, fonts, and additional native component definitions. The current prototype does not load third-party executable code, scripts, or arbitrary WebView extensions. Add-on permissions, provenance, size limits, and compatibility will need explicit review before a catalogue is enabled.

## Root layout

The initial renderer supports vertical_scroll_stack. Padding values are density-independent pixels and must be between 0 and 256.

## Component registry

| Type | Purpose | Supported properties |
| --- | --- | --- |
| clock_widget | Reactive local clock | style |
| list / app_list_container | Scrollable app list | show_labels, max_visible_items, spacing, filter |
| grid | App grid | columns (1–8), show_labels, max_visible_items, spacing |
| dock | Horizontal app strip | show_labels, max_visible_items, spacing |
| stack | Nested card/stack container | children, spacing |

All component types are registered in the native ComponentRegistry. Unknown types are validation errors; the renderer never attempts to execute unknown content. Grid and list components include an in-place search field that matches app labels and package names, case-insensitively.

## Validation limits

- schema_version must match the engine-supported version
- metadata ID and name must not be blank
- at most 100 components per container
- maximum component nesting depth: 12
- max_visible_items: 1–500
- columns: 1–8
- spacing: 0–64 dp
- padding: 0–256 dp

Validation reports a JSON-style path and a human-readable reason. Invalid presets do not replace the currently active preset.

## Filters

exclude_packages supports explicit package-name exclusions. exclude_categories is reserved for a future category provider and is not yet applied by this prototype.

## .omni archive format

The current importer/exporter supports a deliberately narrow ZIP format:

- The archive must contain exactly one file named layout.json.
- The compressed archive is limited to 10 MiB.
- The expanded layout.json is limited to 1 MiB.
- Directory entries, duplicate entries, path-bearing names, and all additional files are rejected.
- The JSON document must pass the same strict schema decoder and validator used for bundled presets.
- Imported documents are stored in the app's private files directory; the active preset ID is persisted separately.
- Android's Storage Access Framework is used for import and export, so Omni does not request broad storage access.

Image assets, localized scripts, JavaScript, Kotlin, shell commands, reflection targets, and binder transaction codes are not supported. Future archive extensions must preserve the declarative-only security boundary and add explicit limits and validation for each resource type.

## Interactions and package security

Interaction entries are inert data until the engine explicitly implements a known trigger/action pair. Presets must not provide arbitrary executable logic. Unknown or unsupported interactions must remain inert.
