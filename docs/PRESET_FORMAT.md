# Omni preset format — schema version 1

Presets are declarative JSON documents. They are data, not executable code.

## Top-level structure

- schema_version: currently 1
- preset_metadata: stable ID, name, author and version fields
- global_styles: shared visual tokens
- interaction_matrix: data-only interaction declarations
- layout_hierarchy: root layout and ordered components

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

All component types are registered in the native ComponentRegistry. Unknown types are validation errors; the renderer never attempts to execute unknown content.

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
