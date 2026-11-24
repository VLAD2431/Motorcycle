# Bike Light Brightness

## Intent and goals

Allow server owners to tune the brightness override of the separate bike-light `ItemDisplay` using `block` and `sky` values in `config.yml`, with the default `15` for both.

## Non-goals and constraints

- This controls the entity's displayed brightness; it does not make the entity emit light onto nearby blocks or alter texture-pack emissive settings.
- Do not change the main bike display or unrelated light-model configuration.
- Follow the Paper API and existing configuration conventions.

## Approaches

- Hard-code full brightness: simplest, but cannot be tuned without rebuilding.
- Configure `block` and `sky` brightness independently: selected because it directly exposes the requested values to server owners.

## Design

Add `motorcycle.visual.light-brightness.block` and `.sky` configuration values, defaulting to `15`. Apply them as the Paper display brightness override to the light `ItemDisplay`, clamping each value to `0..15`. Verify with focused tests where practical and `mvn test-compile`.
