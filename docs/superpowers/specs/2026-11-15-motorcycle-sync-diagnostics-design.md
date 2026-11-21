# Motorcycle sync diagnostics

## Intent

Record enough server-side state to distinguish movement/teleport issues between
the motorcycle seat, rider, display model, and optional passenger seat while
reproducing visible jitter.

## Goals and constraints

- Diagnostics are disabled by default and controlled by a static config setting.
- When enabled, emit one concise console log line per motorcycle per server tick.
- Include tick and bike identity; seat location, velocity, yaw, and passengers;
  rider location; display actual and target locations and teleport result; and
  passenger-seat actual and target locations and teleport result when present.
- Do not alter movement, teleport timing, or entity state for diagnostics.
- No new files, dependencies, commands, or player-facing messages.
- Logging every tick is intentionally high-volume and should only be enabled for
  a brief reproduction.

## Approaches

- Log only detected position jumps: low volume, but can miss client-side visual
  interpolation issues. Not selected because the symptom may not exist in the
  server's position state.
- Log every tick to the console behind a config toggle: captures component
  boundaries and can be disabled after a short recording. Selected for this
  diagnosis.
- Log every five ticks: lower volume, but may miss the ordering/tick context of
  short jitter. Not selected.

## Implementation calls

- Use a config key under `motorcycle.diagnostics`, defaulting to `false`.
- Keep log output in a single structured line per bike/tick.
- Capture teleport outcomes where teleports are issued, then include them in that
  tick's diagnostic line.

## Verification

- Add focused tests for diagnostic line construction/enable gating where
  practical.
- Run the full Maven test suite and `mvn test-compile`.
- Runtime confirmation requires enabling diagnostics briefly on the Paper server
  and comparing a short recording with the visible jitter.
