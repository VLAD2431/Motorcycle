# Hide Plugin Chat Messages Design

## Intent
Ordinary players should not see motorcycle-plugin notifications in chat. Player chat and action-bar messages remain unchanged.

## Scope
- Only chat notifications emitted by `MotorcycleManager` are affected.
- Players with `motorcycle.admin` continue to receive those notifications in chat.
- Players without `motorcycle.admin` do not receive those notifications.
- Command replies and action-bar output are unchanged.

## Approaches
- Hide manager chat notifications from non-admins (chosen): minimal behavior change, preserves admin diagnostics and existing action-bar behavior.
- Move notifications to action bar: not chosen because the user explicitly requested hiding, not relocation.
- Filter every plugin chat message globally: not chosen because command responses are separate and outside the requested scope.

## Constraints
Use existing Paper/Adventure APIs and the existing `motorcycle.admin` permission. Do not change ordinary player chat or other notification channels.
