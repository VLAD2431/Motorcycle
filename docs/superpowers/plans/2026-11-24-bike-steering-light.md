# Bike Steering, Slowdown, and Light Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make steering stronger at low speed, apply Slowness II during install/storage, and show a separate configurable light-model overlay unaffected by decals.

**Architecture:** Keep physics in `PhysicsEngine`, lifecycle/effect control and both `ItemDisplay`s in `MotorcycleManager`, and persist the overlay identity with a PDC marker plus bike ID. Add focused tests for speed-dependent steering; verify broader lifecycle by compilation and existing tests.

**Tech Stack:** Java 21, Paper API 1.21.7, JUnit 5, Mockito, Maven.

**Spec:** `docs/superpowers/specs/2026-11-24-bike-steering-light-design.md`

## Global Constraints

- Use Paper API and Adventure conventions; no legacy ChatColor.
- Do not persist dynamic data in YAML; the light overlay is an entity with PDC identity.
- Keep only UUIDs for long-lived player references.
- Compile Java changes with `mvn test-compile`.

## Review Focus

- Low and high speed endpoints and zero/negative speed: test interpolation and clamping in `PhysicsEngineTest`.
- Missing/blank/invalid light-model config: ensure overlay is disabled rather than spawning a malformed item.
- Reloaded entities and decal application: overlay remains separate and can be paired by bike ID.
- Install/storage cancellation and completion: effect is removed without clearing unrelated potion effects.
- Overlay cleanup on storage, shutdown, and rejected partial spawn: remove alongside the main visual.

---

### Task 1: Speed-dependent steering

**Files:**
- Modify: `src/main/java/VLADMIX90/motorcyclePlugin/MotorcycleManager.java`
- Modify: `src/main/resources/config.yml`
- Create: `src/test/java/VLADMIX90/motorcyclePlugin/PhysicsEngineTest.java`

**Interfaces:**
- `PhysicsEngine.steeringDegreesPerTick(double speedBlocksPerTick)` returns the configured/interpolated absolute angle per tick.
- `MotorcycleManager.updateHeading` applies that rate with the existing direction and persistence behavior.

- [ ] Add a test proving low-speed turn rate exceeds high-speed turn rate, and that the configured minimum/maximum endpoints clamp.
- [ ] Run `mvn -Dtest=PhysicsEngineTest test` and confirm it fails because the method is absent.
- [ ] Implement interpolation from `steering-max-degrees-per-second` at/below `steering-low-speed-kmh` to `steering-min-degrees-per-second` at/above `steering-high-speed-kmh`.
- [ ] Update config defaults/comments and use the helper from `updateHeading`.
- [ ] Run `mvn -Dtest=PhysicsEngineTest test`; expect PASS.

### Task 2: Install/storage slowdown

**Files:**
- Modify: `src/main/java/VLADMIX90/motorcyclePlugin/MotorcycleManager.java`

**Interfaces:** Uses the existing `pendingInstallTasks`, `pendingStorageTasks`, `storageBikes`, `cancelInstall`, and `cancelStorage` lifecycle.

- [ ] Apply `PotionEffectType.SLOWNESS` with amplifier `1` and a refresh duration while each operation is active.
- [ ] Remove only Slowness when operation completes/cancels, including quit/shutdown cleanup paths.
- [ ] Run `mvn test-compile`; expect successful compilation and existing tests.

### Task 3: Separate bike light display

**Files:**
- Modify: `src/main/java/VLADMIX90/motorcyclePlugin/Motorcycle.java`
- Modify: `src/main/java/VLADMIX90/motorcyclePlugin/MotorcycleManager.java`
- Modify: `src/main/java/VLADMIX90/motorcyclePlugin/MotorcyclePlugin.java`
- Modify: `src/main/resources/config.yml`
- Tests: existing motorcycle manager/decal tests as applicable.

**Interfaces:** Add an optional `ItemDisplay lightVisual` to `Motorcycle`; identify it with a new PDC key and the existing bike ID. The configured `motorcycle.visual.light-model` is a namespaced item model, default `minecraft:bike_light`.

- [ ] Add config and PDC key.
- [ ] Spawn the overlay display independently with persistent identity, configured item model, and the same display transform.
- [ ] Discover/re-pair overlay during loaded-entity registration; keep it unchanged when applying/restoring decals.
- [ ] Synchronize overlay transform and remove it with the bike, including failed partial spawns.
- [ ] Run `mvn test-compile`; expect successful compilation and existing tests.

### Task 4: Full verification

**Files:** no additional files.

- [ ] Run `mvn test`; expect all tests to pass.
- [ ] Inspect `git diff --check` and `git status --short`; ensure no generated artifacts are included.
