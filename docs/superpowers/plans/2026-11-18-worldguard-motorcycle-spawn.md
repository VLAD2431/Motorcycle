# WorldGuard Motorcycle Spawn Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional WorldGuard region flag `motorcycle-spawning` that allows only the plugin-tagged motorcycle driver-seat Pig to spawn in regions where the flag is explicitly `allow`, even when normal mob spawning is denied.

**Architecture:** Register a WorldGuard `StateFlag` during the plugin's `onLoad()` when WorldGuard is present. A listener registered only when WorldGuard exists checks the tagged Pig and its region flag after WorldGuard's `HIGH` spawn listener, then clears cancellation only for that exact tagged entity and explicit allow. Set the PDC marker inside the spawn initializer so it exists when `CreatureSpawnEvent` fires.

**Tech Stack:** Java 21, Paper API 1.21.7, WorldGuard Bukkit API (provided/compile only), JUnit 5, Mockito, Maven.

**Spec:** `docs/superpowers/specs/2026-11-18-worldguard-motorcycle-spawn-design.md`

## Global Constraints

- Keep WorldGuard optional: declare `softdepend: [WorldGuard]`; do not bundle its classes.
- Register the custom flag during `onLoad()`, before WorldGuard locks its registry.
- Override cancellation only for a Pig with this plugin's `motorcycle_seat` PDC marker and an explicit custom-flag `ALLOW` at that location.
- Bukkit cancellation ownership is unavailable; this explicit exception may clear another plugin's cancellation for the same tagged seat only.
- Preserve existing spawn cleanup and installation-item retention behavior.
- Run `mvn test-compile` after Java changes and `mvn test` for the suite.

## Review Focus

- Unmarked Pig or other entity in an allowed region must not have cancellation cleared; test in Task 2.
- Tagged seat outside an explicit allow must remain cancelled; test in Task 2.
- Another plugin's cancellation on the one explicitly allowed tagged seat is cleared by accepted design; test in Task 2.
- Missing WorldGuard must not load/link WorldGuard integration classes or prevent plugin startup; test registration decision in Task 1 and conditional listener wiring in Task 3.
- Existing PDC marker must be visible during the spawn event, not only after `World.spawn` returns; test initializer assignment in Task 2.

---

### Task 1: Optional WorldGuard Flag Registration

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/plugin.yml`
- Modify: `src/main/java/VLADMIX90/motorcyclePlugin/MotorcyclePlugin.java`
- Create: `src/main/java/VLADMIX90/motorcyclePlugin/WorldGuardMotorcycleFlag.java`
- Test: `src/test/java/VLADMIX90/motorcyclePlugin/WorldGuardMotorcycleFlagTest.java`

**Interfaces:**
- Produces `WorldGuardMotorcycleFlag.FLAG_NAME`, `WorldGuardMotorcycleFlag.MOTORCYCLE_SPAWNING` and `WorldGuardMotorcycleFlag.registerIfAvailable(JavaPlugin)`; field is null if unavailable or conflicting with a non-StateFlag.

- [ ] **Step 1: Write failing tests** for flag registration choosing the newly registered `StateFlag`, reusing an existing same-name `StateFlag` on `FlagConflictException`, and leaving the integration unavailable when WorldGuard is absent or the name conflicts with a non-state flag.
- [ ] **Step 2: Run the targeted test** with `mvn -Dtest=WorldGuardMotorcycleFlagTest test`; confirm expected failures.
- [ ] **Step 3: Add the WorldGuard 7 Bukkit API** as `provided` using EngineHub's Maven repository and declare `softdepend: [WorldGuard]` in `plugin.yml`.
- [ ] **Step 4: Implement `WorldGuardMotorcycleFlag.registerIfAvailable(JavaPlugin)`** and call it from `MotorcyclePlugin.onLoad()` only when WorldGuard is enabled/present. Avoid resolving WorldGuard classes on the absent-plugin path; log a clear warning for an incompatible existing flag.
- [ ] **Step 5: Run targeted tests and `mvn test-compile`**; expect the new tests and compilation to pass.

### Task 2: Mark Seat Before Spawn Event and Apply the Flag Exception

**Files:**
- Create: `src/main/java/VLADMIX90/motorcyclePlugin/WorldGuardMotorcycleSpawnListener.java`
- Modify: `src/main/java/VLADMIX90/motorcyclePlugin/MotorcycleManager.java`
- Modify: `src/main/java/VLADMIX90/motorcyclePlugin/MotorcyclePlugin.java`
- Test: `src/test/java/VLADMIX90/motorcyclePlugin/WorldGuardMotorcycleSpawnListenerTest.java`
- Test: `src/test/java/VLADMIX90/motorcyclePlugin/MotorcycleSpawnTest.java`

**Interfaces:**
- Consumes: `WorldGuardMotorcycleFlag.MOTORCYCLE_SPAWNING` and existing `MotorcyclePlugin.seatKey()`.
- Produces: `WorldGuardMotorcycleSpawnListener(MotorcyclePlugin)` with handler `onCreatureSpawn(CreatureSpawnEvent)` at `EventPriority.HIGH`, `ignoreCancelled = false`.

- [ ] **Step 1: Write failing listener tests** asserting cancellation is cleared only for a tagged Pig with flag `ALLOW`; cancellation remains unchanged for non-Pig, untagged Pig, flag `DENY`/unset, and unrelated event types. Include cancelled and uncancelled allowed tagged-Pig cases.
- [ ] **Step 2: Run `mvn -Dtest=WorldGuardMotorcycleSpawnListenerTest test`** and verify the failures reflect missing behavior.
- [ ] **Step 3: Move/set the seat PDC marker inside the Pig `World.spawn` initializer** in `MotorcycleManager.spawn()`, before the event dispatches; retain identity/setup behavior and remove the redundant later marker write.
- [ ] **Step 4: Implement `WorldGuardMotorcycleSpawnListener`** querying `BukkitAdapter.adapt(event.getLocation())` with WorldGuard region query for `MOTORCYCLE_SPAWNING`; call `setCancelled(false)` only for tagged Pig and explicit `StateFlag.State.ALLOW`. Do not touch other WorldGuard checks or arbitrary cancellations.
- [ ] **Step 5: Run targeted tests**; expect every allow/deny/entity-identification assertion to pass.

### Task 3: Conditional Listener Registration and Full Verification

**Files:**
- Modify: `src/main/java/VLADMIX90/motorcyclePlugin/MotorcyclePlugin.java`
- Test: `src/test/java/VLADMIX90/motorcyclePlugin/WorldGuardMotorcycleFlagTest.java`
- Test: `src/test/java/VLADMIX90/motorcyclePlugin/MotorcycleSpawnTest.java`

**Interfaces:**
- Consumes: `WorldGuardMotorcycleFlag.MOTORCYCLE_SPAWNING` and `WorldGuardMotorcycleSpawnListener(MotorcyclePlugin)`.

- [ ] **Step 1: Add/extend tests** to verify no integration listener is registered when WorldGuard/flag is unavailable, and existing rejected-spawn cleanup and item-preservation tests still pass.
- [ ] **Step 2: Run the targeted tests** and confirm they fail for missing conditional registration where applicable.
- [ ] **Step 3: Register the WorldGuard listener only when the plugin and flag are available**, after regular plugin initialization; do not create or reference the listener on the no-WorldGuard path.
- [ ] **Step 4: Run `mvn test-compile`, then `mvn test`, then `mvn package`**; require exit code 0 and no test failures.
- [ ] **Step 5: Inspect the final diff** to ensure no unrelated workspace changes were included or overwritten.
