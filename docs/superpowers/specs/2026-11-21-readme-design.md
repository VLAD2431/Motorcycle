# README Design

## Intent

Create a complete `README.md` for the Motorcycle Paper plugin so server
administrators can install and use it, while developers can build, test, and
extend it.

## Goals

- Describe the plugin's motorcycle, physics, battery, storage, particles,
  sounds, zones, and WorldGuard integration.
- Document requirements, installation, Maven commands, commands, permissions,
  configuration locations, and data storage.
- Explain the source layout and the public `GravibikeDecalApi` service briefly.
- Keep all claims aligned with `plugin.yml`, `config.yml`, `pom.xml`, and the
  current Java implementation.

## Non-goals

- Do not redesign plugin behavior or configuration.
- Do not document undocumented server-side features as guaranteed behavior.
- Do not add generated build output or a copied full configuration file.

## Constraints

- Paper API 1.21.7 and Java 21 are the current build targets.
- WorldGuard is an optional runtime dependency; SQLite is used for persisted
  zones in the plugin data folder.
- README examples should use the actual `/moto` command and permissions.
- Documentation should use Markdown and remain concise enough to scan.

## Approach

Use one root README with a user-first order: overview, features, requirements,
installation, usage, commands and permissions, configuration, WorldGuard,
resource-pack sounds/models, persistence, development, and project structure.
This is preferred over a separate admin/developer split because the project is
small and both audiences need the same installation and configuration facts.

## Verification

- Cross-check every command and permission against `plugin.yml` and
  `MotorcycleCommand.java`.
- Cross-check configuration examples against `config.yml`.
- Cross-check build and dependency information against `pom.xml`.
- Inspect the final Markdown for broken sections, unsupported claims, and
  accidental inclusion of `target/` artifacts.
