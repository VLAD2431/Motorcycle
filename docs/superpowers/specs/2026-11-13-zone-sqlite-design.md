# Zone SQLite Migration Design

## Intent and Scope

Move dynamic zone storage from `zones.yml` to `database.db` while retaining the existing in-memory spatial index for fast gameplay checks. Keep `plugin.yml` and `config.yml` unchanged. Do not alter motorcycle seat, display, passenger, or rotation behavior; diagnosing that synchronization issue remains a live-server task.

## Goals

- Persist zones in SQLite with `PRAGMA journal_mode=WAL`.
- Load and save database data asynchronously and expose zones from a concurrent in-memory cache.
- Prevent zone mutation or persistence from overwriting stored data before initial loading completes.
- Import legacy YAML atomically, preserve it as a backup, reject malformed records, and record migration completion only after a successful transaction.
- Verify repeat startup, CRUD persistence, and migration failures with tests; run `mvn test` and `mvn compile`.

## Non-goals

- Change motorcycle physics or investigate runtime synchronization without a live server.
- Remove `plugin.yml` or `config.yml`.
- Silently skip malformed legacy zones.

## Approaches

- Persist each change directly to SQLite asynchronously: selected because it preserves the current manager API and avoids whole-cache rewrite races.
- Queue mutations until initial load finishes: rejected because commands and gameplay could observe ambiguous ordering; instead, reject zone mutations until ready and report that status.
- Keep synchronous persistence for command actions: rejected because file/database work on the server thread violates project constraints.

## Design

Use SQLite in the plugin data folder (`database.db`) with WAL. A dedicated persistence/migration component owns schema creation, strict legacy parsing, transaction boundaries, backup creation, and JDBC work on a dedicated executor. `ZoneManager` owns a `ConcurrentHashMap` cache and spatial lookup; it exposes readiness and a completion stage for startup and shutdown. It loads asynchronously, publishes the complete cache only on success, and rejects create/delete before readiness. Create/delete update cache/index and submit ordered persistence operations without disk work on the main thread. Plugin enable starts loading; zone command actions report not-ready rather than mutating during startup; plugin disable waits for queued saves and closes the executor/database safely.

Legacy `zones.yml` is parsed strictly: every top-level zone must have a valid name, known type, world, and finite ordered coordinate bounds. The original file is copied to a non-overwriting backup before import. Import and migration marker are committed in one SQLite transaction; any parse, copy, or database error leaves migration unmarked and zones unpublished. Existing database state takes precedence on subsequent startup.

## Constraints and Calls

- Use Java 21, Paper API, Maven, and SQLite JDBC available to the plugin.
- Dynamic zone data belongs only in SQLite; `config.yml` remains static configuration.
- Only the main-thread-safe cache and Bukkit world/location access remain on gameplay paths; persistence and YAML parsing run off-thread.
- The exact backup naming and SQL schema are implementation details; backup creation must not overwrite an existing backup.
