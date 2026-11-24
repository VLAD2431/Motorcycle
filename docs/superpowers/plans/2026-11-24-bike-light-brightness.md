# Bike Light Brightness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the bike-light `ItemDisplay` brightness override configurable with independent block and sky values.

**Architecture:** Add two integer configuration values and apply them when spawning the existing light display, clamped to Minecraft's `0..15` range. Keep the main bike display and the current light-model setup unchanged.

**Tech Stack:** Java 21, Paper API 1.21+, Maven, JUnit.

**Spec:** `docs/superpowers/specs/2026-11-24-bike-light-brightness-design.md`

## Global Constraints

- Use Paper API 1.21+ and Java 21.
- Use `mvn test-compile` after Java changes.
- Preserve unrelated existing working-tree changes.
- Brightness override does not create block light or change resource-pack emissive textures.

## Review Focus

- Values below zero or above 15: clamp to the supported `0..15` range.
- Missing brightness configuration: default both components to `15`.
- Block and sky brightness differ: preserve each configured value independently.
- Main bike model: ensure only the light display receives the override.

---

### Task 1: Configurable light display brightness

**Files:**
- Modify: `src/main/resources/config.yml`
- Modify: `src/main/java/VLADMIX90/motorcyclePlugin/MotorcycleManager.java`
- Test: `src/test/java/VLADMIX90/motorcyclePlugin/LightCustomModelDataTest.java` or focused light-brightness test in the same package

**Interfaces:**
- Use Paper `Display.Brightness(int blockLight, int skyLight)` and `ItemDisplay#setBrightness`.
- Add a package-visible static clamp helper only if needed for focused unit testing.

- [ ] **Step 1: Add a failing test** for brightness clamping, asserting in-range values remain unchanged and values below `0` / above `15` become `0` / `15`.
- [ ] **Step 2: Run the focused test** with `mvn -Dtest=LightBrightnessTest test`; confirm it fails because the helper or behavior is not implemented.
- [ ] **Step 3: Add config defaults** `motorcycle.visual.light-brightness.block: 15` and `sky: 15`, documenting the `0..15` range.
- [ ] **Step 4: Apply configured brightness** to the light `ItemDisplay` at spawn using `Display.Brightness`, defaulting missing values to `15` and clamping each independently.
- [ ] **Step 5: Run focused tests and compile** with `mvn -Dtest=LightBrightnessTest test` and `mvn test-compile`.
