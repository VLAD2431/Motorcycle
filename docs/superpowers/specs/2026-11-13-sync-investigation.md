# Seat / display / passenger synchronization: static findings

No physics or entity movement changes were made during the SQLite work.

## Evidence in the current code

- `MotorcycleManager.tick()` calls `drive()` (or idle velocity), then `syncVisual()`, then `updatePassengerSeat()`.
- `updateHeading()` updates the main Pig seat with `setRotation()`. This alone is not evidence that rotation causes positional desynchronization.
- `syncVisual()` independently teleports the ItemDisplay to a position derived from the vehicle, with a configurable forward/height offset. Display teleport and transformation interpolation use `motorcycle.visual.teleport-duration-ticks` (default 3).
- `updatePassengerSeat()` independently teleports the second Pig 0.8 blocks behind the driver seat with `RETAIN_PASSENGERS`. Its teleport return value is not checked, unlike the display teleport.
- These are separate entities using different movement/interpolation paths. Equal server-side targets do not establish equal client-side rendered positions. The comment estimating three ticks of living-entity interpolation is not a runtime measurement.

## What remains unproven

There is no live Paper server/client capture in this environment. Neither `setRotation()` nor interpolation duration is established as the root cause. No fix is claimed.

## Reproduction checks on a server

1. Record Paper/Minecraft version, client version, ping, TPS, resource pack, and current visual offsets/duration.
2. Compare straight driving, turning, braking, and idle motion with driver only and with a passenger; include an observing client.
3. For the same server ticks, capture seat position/yaw/velocity, display actual and target position, passenger-seat actual and target position, passengers, and both teleport results.
4. Distinguish server-side target/teleport failures from client rendering delay before changing rotation, velocity, or interpolation. Change one variable at a time and retain before/after recordings.
