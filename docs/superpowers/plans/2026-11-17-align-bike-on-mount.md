# Align Motorcycle on Mount Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align the motorcycle's cardinal heading to the driver's yaw when they mount.

**Architecture:** On successful first-driver mounting, snap the player's yaw with the existing cardinal-yaw helper and update the bike heading before its existing visual synchronization tick. Keep passenger mounting and movement behavior unchanged.

**Tech Stack:** Java 21, Paper API 1.21+, JUnit, Maven.

**Spec:** Approved in conversation: first driver mount uses player yaw rounded to the nearest 90 degrees; model and seat follow through existing sync; no changes to passenger mounting or movement.

## Global Constraints

- Use Paper API patterns and Adventure text; no legacy ChatColor.
- Compile Java changes with `mvn test-compile`.
- Keep the change surgical and follow existing project conventions.

## Review Focus

- Boundary angles between cardinal directions must use the existing snap helper's behavior; pin with a focused test if not already covered.
- Passenger mounting must not replace the driver's heading; keep heading adjustment after driver-seat mount succeeds.

---

### Task 1: Align Heading on Driver Mount

**Files:**
- Modify: `src/main/java/VLADMIX90/motorcyclePlugin/MotorcycleManager.java`
- Test: `src/test/java/VLADMIX90/motorcyclePlugin/CardinalYawTest.java` and/or existing mount tests.

**Interfaces:**
- Consumes existing `snapToCardinalYaw(float)` helper and `Motorcycle.setHeadingYaw(float)`.
- No new public API.

- [ ] **Step 1: Add or adjust a focused test** to verify cardinal yaw snapping and, where test setup supports it, that successful driver mounting adopts snapped player yaw without changing passenger mounting.
- [ ] **Step 2: Run the focused test** and confirm it fails for the missing mount alignment.
- [ ] **Step 3: After successful driver-seat mount, set the bike heading to the player's yaw snapped by the existing helper.** Do not alter passenger mount flow.
- [ ] **Step 4: Run the focused tests, then `mvn test-compile`.**
- [ ] **Step 5: Review the diff** to ensure only mount alignment and its tests changed.
