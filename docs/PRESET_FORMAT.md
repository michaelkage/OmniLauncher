# Omni preset format (schema version 1)

A preset is a declarative description of a launcher surface. It is data, not executable code.

## Top-level fields

| Field | Type | Purpose |
| --- | --- | --- |
| schema_version | integer | Format version for compatibility checks |
| preset_metadata | object | Stable ID, display name, author and versions |
| global_styles | object | Shared visual tokens |
| interaction_matrix | array | Declarative gesture-to-action mappings |
| layout_hierarchy | object | Root layout node |

## Initial layout components

- vertical_scroll_stack: vertically scrollable root container.
- clock_widget: system clock text.
- app_list_container: launchable application list.

Each component declares a supported type and the properties relevant to that type. Unknown properties are ignored for forward compatibility. Unknown component types should be reported rather than interpreted as executable instructions.

## Interactions

Interaction entries are data-only. The engine must map each recognized action to an explicit, reviewed implementation. Presets must not supply arbitrary Kotlin, JavaScript, shell commands, reflection targets, or binder transaction codes.

## Planned .omni package

A future .omni file may be a ZIP archive containing manifest.json, layout.json, and an assets/ directory. The importer must enforce archive size limits, reject path traversal and unsafe paths, validate the manifest and schema, and treat all content as untrusted. Executable scripts are intentionally excluded.

## Compatibility

The engine rejects unsupported schema versions with a useful error. Additive optional fields can be ignored by older renderers.
