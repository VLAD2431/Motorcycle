# Passenger and Coasting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep passengers in their seat and let driverless bikes coast to a stop.

**Architecture:** Remove automatic passenger promotion in MotorcycleManager. Replace idle horizontal zeroing with existing horizontal, ground collision and jump calculations, retaining rolling resistance and safety gates.

**Tech Stack:** Java 21, Paper API 1.21+, Maven, JUnit 5, Mockito.

**Spec:** `docs/superpowers/specs/2026-11-24-passenger-and-coasting-design.md`

## Global Constraints

- Paper API 1.21+, Java 21, Maven.
- No new dependencies, configuration or data storage changes.
- No unrelated refactoring or remote publication.

## Review Focus

- Occupied rear seat must not prevent a new driver boarding.
- Cancelled dismount must retain rider mapping and seat assignments.
- Reverse coasting must approach zero without changing direction.
- Dismount during a jump must allow landing without initiating another jump.
- Storage and no-ride zones must not gain uncontrolled horizontal movement.

### Task 1: Keep passenger seat assignments

**Files:** Modify `src/main/java/VLADMIX90/motorcyclePlugin/MotorcycleManager.java`; create `src/test/java/VLADMIX90/motorcyclePlugin/MotorcycleCoastingTest.java`.

**Interfaces:** Keep existing mount, onDismount and removeRider public interfaces; remove private `promotePassenger(Motorcycle)` and its callers.

- [ ] Add failing Mockito regression tests: removing a driver never invokes passenger.leaveVehicle or driverSeat.addPassenger(passenger); a new driver may mount while the rear seat is occupied; cancelled dismount preserves mappings.
- [ ] Run `mvn test -Dtest=MotorcycleCoastingTest` and confirm behavioral failures.
- [ ] Remove promotion and the mount guard that assumes passengers must be promoted. Preserve successful-dismount mapping cleanup.
- [ ] Run focused tests and `mvn test-compile`; expect success.

### Task 2: Driverless coasting

**Files:** Same manager and test file.

**Interfaces:** Update private `applyIdleHoverVelocity(Motorcycle)` or replace it with a clearly named driverless movement helper; reuse `calculateHorizontalVelocity`, `resolveGroundMovement`, `calculateJumpVelocity` and existing rolling deceleration.

- [ ] Add failing tests asserting nonzero horizontal motion after driver exit, progressive forward/reverse deceleration to zero, zero-speed hovering, jump continuation and obstacle/safety handling. Use the existing mocked-world test patterns and reflective access to private tick/helpers if necessary; avoid production API changes solely for tests.
- [ ] Run focused tests and confirm failures before changing movement.
- [ ] Apply horizontal velocity through the existing terrain/jump resolver without driver input; release jump-held state. Retain storage/no-ride restrictions.
- [ ] Run `mvn test-compile` and `mvn test`; expect all tests passing.
- [ ] Review `git diff --check` and the full diff for unwanted changes. Record server-only checks honestly; package with `mvn package` if producing the updated jar.

## Execution

Recommended: native execution in this session, because both small tasks change the same manager and share their fixtures. Review the approved scope and tests before implementation; do not push or publish.
