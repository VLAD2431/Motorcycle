# Cardinal Motorcycle Placement Design

## Intent
At initial motorcycle deployment, the motorcycle model must use only `0`, `90`, `180`, or `270` degrees. Driving steering remains smooth and unchanged.

## Approaches
- Snap the deployment heading, which already occurs in `MotorcycleManager.spawn`; preserve this behavior and remove arbitrary model yaw offset from the initial visual orientation. Recommended: this directly limits initial placement without changing driving.
- Snap heading on every update: rejected because it would make steering cardinal rather than smooth.
- Change only `config.yml`: rejected because users can still configure arbitrary offsets.

## Constraints and scope
Keep existing nearest-cardinal placement semantics and the `CardinalYawTest` coverage. Do not change driving, pitch/roll lean, or the model calibration settings for subsequent visual updates unless necessary to guarantee the initial angle.

## Verification
Add a focused regression test proving initial model yaw is cardinal even when a non-cardinal model yaw offset is configured; run tests and `mvn test-compile`.
