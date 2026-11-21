# Motorcycle Sync Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add opt-in per-tick console diagnostics to identify server-side divergence among motorcycle seat, rider, display, and passenger seat.

**Architecture:** Capture entity positions and teleport outcomes in the existing synchronous motorcycle tick. Format a single line per bike with a small package-local formatter that can be unit tested; emit only when the config flag is enabled.

**Tech Stack:** Java 21, Paper API 1.21+, Maven, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-11-15-motorcycle-sync-diagnostics-design.md`

## Global Constraints

- Diagnostics default to disabled under `motorcycle.diagnostics`.
- When enabled, emit one concise console line per bike per tick.
- Include tick/bike identity; seat location, velocity, yaw, passengers; rider location; display actual/target location and teleport result; optional passenger-seat actual/target and teleport result.
- Do not alter movement or teleport behavior to collect diagnostics.
- Do not add files at runtime, dependencies, commands, or player messages.
- Paper API takes priority; all entity access remains on the main thread.

## Review Focus

- Diagnostics disabled: no per-tick log output. Test the gate before logger invocation.
- No rider or passenger seat: formatter still emits valid line with explicit absent values. Test null optional entities.
- Entity target in another world: output must remain unambiguous and formatting must not call cross-world distance. Test world-qualified location rendering.
- Teleport rejected: outcome must be represented as false, not silently assumed successful. Capture actual Bukkit teleport return values.
- High-volume logging: only interpolate/format data when the config flag is enabled. Verify the config branch guards formatter invocation.

---

### Task 1: Per-tick sync diagnostic capture

**Files:**
- Create: `src/main/java/VLADMIX90/motorcyclePlugin/MotorcycleSyncDiagnostics.java`
- Modify: `src/main/java/VLADMIX90/motorcyclePlugin/MotorcycleManager.java`
- Modify: `src/main/resources/config.yml`
- Test: `src/test/java/VLADMIX90/motorcyclePlugin/MotorcycleSyncDiagnosticsTest.java`

**Interfaces:**
- Produce package-local `static String format(...)` with tick, bike id, entity snapshots, and optional teleport outcomes; location snapshots are rendered as world name and coordinates without distance calculations.
- `syncVisual` and `updatePassengerSeat` return whether their teleport succeeded; no teleport needed counts as true.

- [ ] **Step 1: Write formatter tests** for all entity/teleport fields, absent rider/passenger seat, and world-qualified coordinates.
- [ ] **Step 2: Run** `mvn -Dtest=MotorcycleSyncDiagnosticsTest test`; expect compilation/test failure because formatter does not exist.
- [ ] **Step 3: Implement** the package-local formatter and verify its output fields.
- [ ] **Step 4: Integrate** a `motorcycle.diagnostics.sync.enabled: false` config toggle; return teleport results from existing sync methods; after sync calls, emit one formatted line only when enabled.
- [ ] **Step 5: Run** `mvn test` and `mvn test-compile`; expect all tests and compilation to pass.
