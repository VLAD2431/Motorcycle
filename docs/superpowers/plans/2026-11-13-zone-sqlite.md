# Zone SQLite Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace dynamic `zones.yml` persistence with safe asynchronous SQLite storage and one-time legacy import.

**Architecture:** A persistence component owns SQLite schema, WAL, YAML import, and JDBC operations on a dedicated executor. `ZoneManager` retains its spatial index and exposes a concurrently published cache, readiness, asynchronous CRUD, and a drain/close lifecycle; plugin startup and shutdown coordinate those stages.

**Tech Stack:** Java 21, Paper API 1.21.7, Maven, SQLite JDBC, JUnit 5, Mockito.

**Spec:** `docs/superpowers/specs/2026-11-13-zone-sqlite-design.md`

## Global Constraints

- Use SQLite `database.db` with `PRAGMA journal_mode=WAL` for dynamic zone data.
- Perform database and file work asynchronously, with a `ConcurrentHashMap` cache.
- Do not mutate or persist zones until initial loading and migration finish successfully.
- Preserve `zones.yml` as a non-overwritten backup; reject malformed entries and mark migration only after successful transactional import.
- Keep `plugin.yml` and `config.yml`; do not modify motorcycle physics or claim the sync issue fixed.

## Review Focus

- Startup/read failure must not publish an empty cache or permit overwriting existing rows; test failed readiness and unchanged database contents.
- A malformed YAML record must abort the whole import and leave migration unmarked; test unknown type/missing coordinate.
- Existing backup must never be overwritten; test pre-existing backup and migration failure.
- Repeated startup must not duplicate or re-import zones; test reopening the same database.
- Rapid create/delete writes must preserve ordering and final database state; test serialized async mutations.

---

### Task 1: SQLite Store and Migration

**Files:**
- Create: `src/main/java/VLADMIX90/motorcyclePlugin/ZoneStore.java`
- Modify: `pom.xml`
- Test: `src/test/java/VLADMIX90/motorcyclePlugin/ZoneStoreTest.java`

**Interfaces:**
- Produces: `ZoneStore(Path dataFolder, Logger logger)`, synchronous `List<Zone> load()`, `upsert(Zone)`, `delete(String)`, `close()`; manager invokes all JDBC/file operations on its dedicated executor.

- [ ] Write tests for WAL/schema, YAML import and backup, repeated open, and malformed import rollback.
- [ ] Run `mvn -Dtest=ZoneStoreTest test` and confirm failure before implementation.
- [ ] Implement SQLite schema (`zones` plus migration metadata), WAL initialization, strict YAML validation, transactional import, non-overwriting backup, ordered CRUD, and close semantics.
- [ ] Add SQLite JDBC dependency, then rerun `mvn -Dtest=ZoneStoreTest test`.

### Task 2: Async Zone Cache Lifecycle

**Files:**
- Modify: `src/main/java/VLADMIX90/motorcyclePlugin/ZoneManager.java`
- Test: `src/test/java/VLADMIX90/motorcyclePlugin/ZoneManagerTest.java`

**Interfaces:**
- Consumes: `ZoneStore` from Task 1.
- Produces: `CompletableFuture<Void> loadAsync()`, `boolean isReady()`, existing `Zone create(...)` / `boolean delete(String)` with ordered asynchronous persistence, `CompletableFuture<Void> shutdown()`; retain read-only gameplay lookups.

- [ ] Add tests that lookups do not expose a partial cache and mutations before readiness cannot write.
- [ ] Run `mvn -Dtest=ZoneManagerTest test` and confirm new tests fail.
- [ ] Implement concurrent cache publication after successful load, readiness checks, async ordered persistence, and spatial-index invalidation; remove YAML save/load from the manager.
- [ ] Verify `mvn -Dtest=ZoneManagerTest test` including CRUD persistence and write ordering.

### Task 3: Plugin and Command Lifecycle

**Files:**
- Modify: `src/main/java/VLADMIX90/motorcyclePlugin/MotorcyclePlugin.java`
- Modify: `src/main/java/VLADMIX90/motorcyclePlugin/MotorcycleCommand.java`
- Test: `src/test/java/VLADMIX90/motorcyclePlugin/ZoneManagerTest.java` (or focused lifecycle test)

**Interfaces:**
- Consumes: `ZoneManager.loadAsync()`, `isReady()`, cache CRUD with async persistence, and `shutdown()` from Task 2.

- [ ] Test that zone create/delete commands do not mutate while loading and communicate unavailable state.
- [ ] Start async load during enable; on disable await queued persistence and close storage without a main-thread save.
- [ ] Adapt command handlers to readiness checks without touching `plugin.yml` or `config.yml`.
- [ ] Run targeted tests and inspect that no zone-path JDBC/YAML work executes synchronously.

### Task 4: Full Verification

**Files:**
- Test: all Maven tests.

- [ ] Run `mvn test`.
- [ ] Run `mvn compile`.
- [ ] Review diff to confirm existing user changes are preserved and motorcycle physics files are untouched.

## Execution record and review rulings

- Ruling: retained the existing synchronous cache CRUD API with asynchronous per-row persistence, as specified in the design; the original plan's future-returning CRUD signatures conflicted with that design. A command response acknowledges cache mutation, not a durable commit. Cost: disk/SQL failure after the response is logged and can lose that change on restart; no rollback/durable-acknowledgement API was added.
- Ruling: shutdown waits for the serialized storage queue and close, but never waits for Bukkit cache publication. This may delay disable on slow storage, intentionally preferring write draining over discarding queued changes.
- Ruling: malformed YAML is rejected before backup creation; after backup creation a SQL rollback preserves the backup and leaves the marker absent. Existing backup blocks a retry rather than overwriting evidence; operator intervention is required to resolve that case.
- SQLite JDBC is test-scoped; runtime uses Paper's driver per project policy.
- Independent review found shutdown/mutation and delayed-publication races. Added two tests, observed both fail, then added serialized lifecycle transitions, readiness reset, and exceptional late publication. Full suite: 34 tests passing after the fix.
- Deferred test gaps: command handler behavior is not directly covered; backup-copy I/O failure/concurrent external modification are not separately simulated. Non-overwriting `Files.copy` is used.
- No commits created: the checkout includes pre-existing user changes. No physics, `config.yml`, or `plugin.yml` edits were made by this task.
