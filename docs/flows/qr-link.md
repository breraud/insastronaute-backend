# Permanent QR Link

The printed QR code always contains the API URL configured by `QR_LINK_PUBLIC_URL`, normally `https://api.example.com/q`. Administrators never need to regenerate the QR code when its behavior changes.

## Redirect mode

With `challengeModeEnabled=false`, the public request:

```http
GET /q
```

returns `302 Found` to the configured external HTTPS `redirectUrl`. This request does not require a session, does not start an OIDC login, and never changes the visitor's user record.

## Challenge mode

With `challengeModeEnabled=true`, `GET /q` checks the current browser session before loading INSAstral. An anonymous visitor is redirected directly to Keycloak with `QR_LINK_SCAN_LANDING_URL` preserved as the post-login destination. An authenticated visitor is redirected straight to that landing URL. The `/scan` page therefore loads only after authentication and lives outside the normal application layout. It calls:

```http
POST /api/scan
X-XSRF-TOKEN: <token>
```

The API checks challenge mode again when the landing submits the scan:

- a first scan changes `users.scanned` from `false` to `true` and returns `SCANNED`;
- any later scan leaves the permanent flag unchanged and returns `ALREADY_SCANNED`;
- if challenge mode was disabled during authentication, the API returns `404` and does not load or modify the user.

The landing displays the returned success state or API error. A `404` renders a generic missing-page view so the scan page is not exposed outside active periods.

## Admin configuration

Both routes require `ROLE_ADMIN`:

```http
GET /api/admin/qr-link
PUT /api/admin/qr-link
```

Update payload:

```json
{
  "redirectUrl": "https://example.org/current-page",
  "challengeModeEnabled": false
}
```

`redirectUrl` must be an absolute HTTPS URL. Disabling challenge mode never resets `users.scanned`.
