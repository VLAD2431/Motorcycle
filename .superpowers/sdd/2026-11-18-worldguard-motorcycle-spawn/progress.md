# SDD ledger - plan: docs/superpowers/plans/2026-11-18-worldguard-motorcycle-spawn.md

Pre-flight: Task 1 produces a registered optional flag consumed by Tasks 2 and 3; Task 2 listener and spawn initializer behavior are consumed by Task 3 registration. Existing `seatKey()` and PDC usage confirmed in MotorcyclePlugin/MotorcycleManager. No interface conflict found.
Ruling: Stay in existing dirty main checkout — a fresh worktree would omit the user's relevant uncommitted plugin/config changes, while the user explicitly authorized code edits in this shared workspace. Cost if wrong: unrelated local changes remain present during build; scope is constrained to required files and final diff will be inspected.
Ruling: Replace bash-dependent task workspace scripts with this plan-scoped ledger — Bash is unavailable on this Windows environment and the script invocation timed out/failed; plan record remains repository-local and isolated by path. Cost if wrong: scripted brief/task verification is not available, so each test/verification must be recorded manually.

Ruling: Passenger-seat Pig is not included in the `motorcycle-spawning` exception — the approved design explicitly makes passenger seats a non-goal and limits the exception to the tagged driver seat. Cost if wrong: passenger seat deployment can still be denied by WorldGuard mob-spawning rules in those regions.
Task 1: complete (tests: `mvn -Dtest=WorldGuardMotorcycleFlagTest test` → 1/1; `mvn test-compile` → success).
Task 2: complete (tests: `mvn "-Dtest=MotorcycleSpawnTest,WorldGuardMotorcycleSpawnListenerTest,WorldGuardMotorcycleFlagTest" test` → 7/7; `mvn test-compile` → success).
Task 3: complete (tests: `mvn test` → 50/50; `mvn package` → success).
Final review: fresh reviewer found passenger Pig is not included; deferred as intentional non-goal under approved design. No other blocking findings.
