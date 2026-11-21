# WorldGuard Motorcycle Spawn Design

## Intent

Allow administrators to permit motorcycle deployment in selected WorldGuard
regions while keeping the region's general `mob-spawning: deny` policy. The
motorcycle plugin must continue to load and work when WorldGuard is absent.

## Goals and Non-Goals

- Add a custom WorldGuard region state flag named `motorcycle-spawning`.
- An explicit `allow` exempts only the motorcycle's tagged driver-seat Pig
  spawn from WorldGuard's general mob-spawn denial.
- Other mob spawns and unrelated region protections remain unchanged.
- Preserve existing behavior outside explicitly allowed regions and when
  WorldGuard is not installed.
- Do not add a hard WorldGuard dependency or bundle WorldGuard classes.

Non-goal: permit passenger seats, arbitrary pigs, or other entities through
this exception. The item display is not a mob and should not require the flag.

## Approaches

1. Custom WorldGuard region flag (selected): query `motorcycle-spawning` for
   the tagged driver-seat entity and narrowly exempt that spawn when allowed.
   Administrators use familiar per-region flags; requires optional WorldGuard
   API integration and careful event ordering.
2. Plugin-owned region-name allowlist: simpler dependency-wise, but duplicates
   WorldGuard region configuration and has less predictable overlap semantics.

The custom flag was selected to keep the policy with the region itself. A
blanket `mob-spawning` allow is rejected because it permits unrelated mobs.

## Behavior and Constraints

- Register the custom flag during plugin loading, handling the case where the
  flag name is already registered without crashing server startup.
- Declare WorldGuard as a soft dependency and compile against its API with
  provided scope; runtime integration is inactive when WorldGuard is absent.
- On a spawn event, identify only the motorcycle driver-seat Pig using its
  plugin PDC marker, query the custom flag at that location, and override
  WorldGuard's mob-spawn cancellation only for an explicit `allow` result.
- Bukkit exposes a shared cancellation state and does not identify which plugin
  cancelled a spawn event. Consequently, when the custom flag is explicitly
  `allow`, the plugin will clear cancellation for the tagged driver-seat Pig;
  this narrowly scoped override can also clear a cancellation made by another
  plugin for that same spawn. No other entity or location is affected.
- Existing installation failure handling remains in place as a fallback.

## Acceptance Criteria

- In a region with `mob-spawning: deny` and `motorcycle-spawning: allow`, the
  motorcycle driver seat can spawn and the motorcycle is usable.
- Ordinary mob spawning remains denied in that region.
- Without `motorcycle-spawning: allow`, the existing denial remains effective.
- For the tagged driver seat in an explicitly allowed region, other plugins'
  cancellations may also be cleared because Bukkit does not expose cancellation
  ownership. This limitation is accepted; no other entity is affected.
- Plugin startup and motorcycle behavior remain unchanged without WorldGuard.
- Tests cover flag selection, entity identification, denial preservation, and
  optional-WorldGuard behavior; `mvn compile package` succeeds.

## Remaining Implementation Call

Use WorldGuard's supported region query and event APIs in a narrowly scoped
listener. Confirm event-priority behavior against the WorldGuard version
targeted by the build before choosing the exact cancellation integration.
