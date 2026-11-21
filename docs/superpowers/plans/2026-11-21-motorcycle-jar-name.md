# Motorcycle JAR Name Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the plugin artifact as `Motorcycle.jar` without the Maven version suffix.

**Architecture:** Set Maven's `build.finalName` while leaving the project `artifactId` and version unchanged.

**Tech Stack:** Maven, Java 21, Paper API plugin.

**Spec:** User-approved request in conversation: the compiled file must be named `Motorcycle` without a version suffix.

## Global Constraints

- Keep the existing Maven coordinates and project version unchanged.
- Do not modify unrelated working-tree changes.
- Verify Java compilation with `mvn test-compile`.

## Review Focus

- The output must be exactly `target/Motorcycle.jar`, not `Motorcycle-<version>.jar`.
- Existing Maven coordinates must remain available for dependency resolution.

---

### Task 1: Configure Artifact Name

**Files:**
- Modify: `pom.xml` in the existing `<build>` section.

**Interfaces:**
- Consumes: Existing Maven project configuration.
- Produces: Maven artifact with final name `Motorcycle`.

- [ ] **Step 1: Add `<finalName>Motorcycle</finalName>` under `<build>` in `pom.xml`.
- [ ] **Step 2: Run `mvn test-compile` and verify compilation succeeds.
- [ ] **Step 3: Run `mvn package -DskipTests` and verify `target/Motorcycle.jar` exists without a version suffix.
