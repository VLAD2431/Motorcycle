# Hide Plugin Chat Messages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hide motorcycle-plugin manager notifications in chat from players without `motorcycle.admin`.

**Architecture:** Apply the existing admin permission check at `MotorcycleManager.send`, the common path for motorcycle gameplay notifications. Leave direct action-bar sends, command replies, and player-to-player chat untouched.

**Tech Stack:** Java 21, Paper API, Adventure Components, Maven.

**Spec:** `docs/superpowers/specs/2026-11-24-hide-plugin-chat-design.md`

## Global Constraints
- Use Paper API and Adventure components; no legacy ChatColor.
- Keep the change scoped to the manager notification sender.
- Compile Java changes with `mvn test-compile`.

## Review Focus
- Non-admin players must not receive manager notifications in chat; cover by testing the permission guard.
- Admin players must continue receiving manager notifications; cover in the same focused test.
- Action-bar delivery must remain untouched; ensure test/inspection confirms `sendActionBar` is not routed through the changed sender.
- Command responses must remain untouched; ensure no changes to `MotorcycleCommand`.
- Player-to-player chat must remain untouched; ensure no global chat event/filter is added.

---

### Task 1: Restrict manager chat notifications

**Files:**
- Modify: `src/main/java/VLADMIX90/motorcyclePlugin/MotorcycleManager.java`, method `send(Player, String)`
- Test: `src/test/java/VLADMIX90/motorcyclePlugin/MotorcycleManagerTest.java` (add a focused test only if the current test utilities support constructing the manager/player interaction cleanly)

**Interfaces:**
- Consumes the existing `player.hasPermission("motorcycle.admin")` permission contract.
- Leaves `sendActionBar(Player, Component)` unchanged.

- [ ] **Step 1: Add a focused test** proving notifications are sent for a player with `motorcycle.admin` and suppressed for a player without it, using existing project test conventions.
- [ ] **Step 2: Run the focused test** with `mvn -Dtest=MotorcycleManagerTest test`; confirm the non-admin case fails before implementation. If manager interaction cannot be tested with existing test utilities, document that limitation and retain compile plus source-level scope verification instead of introducing broad test scaffolding.
- [ ] **Step 3: Guard `MotorcycleManager.send(Player, String)`** so it returns without sending when the player lacks `motorcycle.admin`; keep MiniMessage conversion and `sendMessage` behavior for admins unchanged.
- [ ] **Step 4: Run verification** with `mvn test-compile` and, when available, `mvn -Dtest=MotorcycleManagerTest test`; confirm compilation and focused tests pass.
