# r/place Frontend Integration Flow

## Authentication and CSRF

Every r/place HTTP endpoint is authenticated. Before a browser sends `POST /api/place/pixel`,
fetch `GET /api/csrf` and send the returned cookie token in `X-XSRF-TOKEN`.

## Snapshot and real-time synchronization

`GET /api/place/canvas` returns an `image/png`. Its mandatory `X-Place-Revision` response header
is the revision of that exact snapshot. Draw the PNG at its natural dimensions and parse the
header as the applied revision.

Connect to `/ws/place` with STOMP and subscribe to `/topic/canvas-updates`. Buffer events while a
snapshot is loading. Two message shapes are published, and neither contains `userId`:

```json
{ "type": "PIXEL", "x": 12, "y": 34, "color": "#AABBCC", "revision": "43" }
```

```json
{ "type": "CANVAS_RESET", "revision": "44" }
```

After loading a snapshot at revision `N`, ignore events with revision `<= N`, apply a `PIXEL` only
when its revision is `N + 1`, and advance the applied revision. Reload the snapshot on a revision
gap, any `CANVAS_RESET`, or every WebSocket reconnect. The same buffering rules apply during each
reload, so recovery cannot overwrite a newer event with an older snapshot.

## Pixel placement

Send `POST /api/place/pixel` with JSON:

```json
{ "x": 12, "y": 34, "color": "#AABBCC" }
```

`color` must be uppercase or lowercase `#RRGGBB`; coordinates must be inside the current canvas.
On success the response is:

```json
{
  "x": 12,
  "y": 34,
  "color": "#AABBCC",
  "revision": "43",
  "cooldownType": "CHALLENGE",
  "remainingCooldownMs": 30000,
  "nextPlacementAt": "2026-07-23T14:30:00Z"
}
```

`cooldownType` is `BASE` or `CHALLENGE`. Start the successful placement cooldown immediately
from `nextPlacementAt`, falling back to `remainingCooldownMs` when necessary. If a successful HTTP
placement cannot be reconciled with the live revision stream, reload the authoritative snapshot.

The backend first uses an earned bonus pixel right when one is available. A bonus right is earned
for each complete configured ratio of challenge points and consumes no challenge points. If no
bonus right remains, the base cooldown path is used.

If the selected cooldown is active, the API responds with HTTP 429:

```json
{
  "status": 429,
  "error": "Too Many Requests",
  "code": "PLACE_COOLDOWN",
  "message": "Vous devez attendre avant de pouvoir poser un nouveau pixel.",
  "remainingCooldownMs": 180000
}
```

`Retry-After` is also returned as a whole number of seconds. The frontend should disable the
selected placement action until that duration has elapsed, then retry only after user action.

Other stable placement failures are HTTP 400 with `code: "PLACE_INACTIVE"` or
`code: "PLACE_INVALID_COORDINATES"`. Only `PLACE_INACTIVE` blocks placement for the event state.
On `PLACE_INVALID_COORDINATES`, reload the snapshot/configuration because the canvas may have been
resized. Use `code`, not status alone or localized `message`, to choose client behavior.
`PLACE_RESIZE_IN_PROGRESS` means an exclusive resize lease is active; retry after reloading rather
than starting cooldown or consuming a bonus right.

## Event configuration

Only administrators use `GET` and `PUT /api/admin/place/config`. The payload contains `isActive`,
ISO-8601 `startDate` and `endDate`, `canvasWidth`, `canvasHeight`, `baseCooldownMs`,
`challengeCooldownMs`, and `challengeRatio`. Width and height are each limited to `1..2000`, for
a maximum canvas of `2000 x 2000`.

When either dimension changes, the update must also contain exactly one resize mode:

```json
{ "canvasWidth": 1200, "canvasHeight": 800, "resizeMode": "PRESERVE_CENTERED" }
```

`PRESERVE_CENTERED` copies the centered overlapping region, including centered cropping when the
new canvas is smaller. `RESET` creates a blank canvas. Omit `resizeMode` when dimensions do not
change. Invalid updates use stable codes `PLACE_RESIZE_MODE_REQUIRED` and
`PLACE_DIMENSIONS_TOO_LARGE`. A successful resize increments the revision and publishes
`CANVAS_RESET`, requiring player clients to reload.

All externally visible revisions (header, REST JSON, and STOMP JSON) are canonical positive decimal
strings within signed 64-bit range. Compare and increment them with arbitrary-precision integers,
never JavaScript `Number`.
