# Cardinal Motorcycle Placement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure initial motorcycle model placement uses only `0`, `90`, `180`, or `270` degrees without changing steering.

**Architecture:** Keep existing heading snapping in `MotorcycleManager.spawn`. Ensure the first visual transform uses that snapped heading without adding arbitrary configured model yaw; later visual updates keep the current calibration behavior.

**Tech Stack:** Java 21, Paper API 1.21+, JUnit 5, Maven.

**Spec:** `docs/superpowers/specs/2026-11-17-cardinal-motorcycle-placement-design.md`

## Global Constraints
- Do not change smooth steering or pitch/roll lean.
- Initial model yaw is restricted to `0`, `90`, `180`, or `270` degrees.
- Verify Java changes with `mvn test-compile`.

## Review Focus
- A non-cardinal configured model yaw offset must not make the initial model yaw non-cardinal; cover with the regression test in `CardinalYawTest`.
- Subsequent steering continues to accept smooth headings; preserve existing steering code and cardinal snap tests.

---

### Task 1: Constrain Initial Model Yaw

**Files:**
- Modify: `src/main/java/VLADMIX90/motorcyclePlugin/MotorcycleManager.java`
- Test: `src/test/java/VLADMIX90/motorcyclePlugin/CardinalYawTest.java`

**Interfaces:**
- Existing `static float snapToCardinalYaw(float yaw)` remains unchanged.
- Existing visual sync logic distinguishes the first transform (`previous == null`) from later updates.

- [ ] Add a test for first-placement yaw with a non-cardinal configured model offset, asserting initial yaw remains cardinal.
- [ ] Run `mvn test -Dtest=CardinalYawTest` and verify the new assertion fails for the expected non-cardinal result.
- [ ] Adjust initial visual yaw in `MotorcycleManager.syncVisual` to use snapped heading without model yaw offset only for the first transform; preserve the offset on subsequent updates.
- [ ] Run `mvn test -Dtest=CardinalYawTest` and `mvn test-compile`.
