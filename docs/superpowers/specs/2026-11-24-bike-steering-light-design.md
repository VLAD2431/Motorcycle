# Bike Steering, Handling Slowdown, and Light Model

## Intent and goals

Make the bike turn more readily at low speed and less readily at high speed. Apply Slowness II while a player installs or stores a bike. Add a separately configured `bike_light` item model as a second visual layer that remains present when the bike's main model receives a decal.

## Non-goals and constraints

- Do not change the bike's existing movement, installation, storage, or decal workflows beyond the requested behaviors.
- Minecraft's entity API cannot make an item texture emit light onto nearby blocks. The configured light model must use emissive textures in the client resource pack; this feature only displays and synchronizes that overlay.
- Keep the existing main `ItemDisplay` and its decal data intact. Track the overlay as a separate persistent display associated with the same bike ID.
- Follow project rules: Paper API, Adventure text, and no dynamic YAML data storage.

## Approaches

- Steering: use one fixed inverse-speed formula, or interpolate between configured low-speed and high-speed steering rates. Choose interpolation for predictable tuning and an explicit range.
- Light: replace the main model when a decal is applied, or add an independent overlay display. Choose an independent display so decals cannot remove the light model.
- Slowdown: add Slowness II for the duration of each install/storage operation and remove the effect on completion, cancellation, or quit; avoid altering movement values. Choose this so the existing location-based cancellation rules remain authoritative.

## Design

- Interpolate steering rate from a configurable maximum rate at low speed to a configurable minimum rate at high speed, clamping outside the configured speed range. Keep the existing low-speed threshold setting where compatible and update config comments/defaults to state the range clearly.
- Apply PotionEffectType.SLOWNESS at amplifier 1 during both installation and nearby storage. Clear only the operation's effect when it ends or is cancelled; ensure shutdown/quit cleanup follows current task cancellation paths and does not leave a long effect behind.
- Add a config path for the light model (default `bike_light`). Spawn a second persistent `ItemDisplay`, identified by the bike ID and its own PDC marker. Synchronize its transform and lifecycle with the existing visual, but never replace it when applying/restoring a decal. Ensure it is removed with the bike and can be rediscovered after chunk load/restart.
- Add focused tests for steering at low/high speed and configuration clamping, plus lifecycle/decal behavior where existing test seams allow. Compile with `mvn test-compile`.

## Builder decisions

Choose sensible config key names/default rates consistent with the existing config. Treat invalid or empty light model configuration as disabling the overlay. Preserve unrelated working-tree changes.
