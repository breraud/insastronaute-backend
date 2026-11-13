# Game Assets Admin Integration

This document describes how the admin frontend publishes a new Unity WebGL build and how the user frontend reads the active build URL.

## Endpoints

| Audience | Method | Path | Auth |
| --- | --- | --- | --- |
| User/public frontend | `GET` | `/api/public/game` | Public |
| Admin frontend | `POST` | `/api/admin/game/assets` | `ADMIN` role + CSRF |

## Public Read Flow

The user frontend reads the currently active game build with:

```http
GET /api/public/game
```

Example response:

```json
{
  "version": "2026.1.0",
  "baseUrl": "https://s3.example.com/insastronaute-public/game/2026.1.0/",
  "indexUrl": "https://s3.example.com/insastronaute-public/game/2026.1.0/index.html",
  "loaderUrl": "https://s3.example.com/insastronaute-public/game/2026.1.0/Build/game.loader.js",
  "manifestUrl": "https://s3.example.com/insastronaute-public/game/2026.1.0/asset-manifest.json",
  "fileCount": 308,
  "totalSizeBytes": 42000000,
  "publishedAt": "2026-07-01T10:00:00Z"
}
```

Use `indexUrl` when embedding the full Unity page, or use `baseUrl` as the root URL if the frontend loads Unity manually.

`manifestUrl` points to the optional chunk manifest. A client must keep the
historical direct loading path when the manifest is absent or returns `404`.

The URL is versioned through `game/{version}/`, so clients can cache game files aggressively without receiving stale assets after a new publication.

## Admin Panel UI

Recommended fields:

| Field | Type | Notes |
| --- | --- | --- |
| Current version | Read-only | From `GET /api/public/game` |
| Current build URL | Read-only link | `baseUrl` or `indexUrl` |
| Published at | Read-only | `publishedAt` |
| File count | Read-only | `fileCount` |
| Total size | Read-only | `totalSizeBytes` |
| New version | Text input | Required |
| Build folder | Directory picker | Required |

Version rules:

- Allowed characters: letters, digits, `.`, `_`, `-`.
- Maximum length: 80 characters.
- Examples: `2026.1.0`, `2026-07-01`, `v1.4.2`.
- The new version must be different from the active version.

Directory picker:

```html
<input type="file" webkitdirectory multiple />
```

The selected folder may be:

- `public/game`
- `game`
- the direct build root containing `index.html`

The backend normalizes these prefixes before uploading to S3.

## Required Build Files

The uploaded folder must contain:

```text
index.html
Build/game.data.br
Build/game.framework.js.br
Build/game.loader.js
Build/game.wasm.br
```

Other files, such as `StreamingAssets`, `TemplateData`, `ServiceWorker.js`, and `manifest.webmanifest`, should be sent too. The API uploads every provided file under the active version folder.

## Chunked Assets

Publication does not change the Unity export or the admin workflow. The
backend automatically handles every selected file:

- files up to and including `2,097,152` bytes keep their historical path;
- larger files are split byte-for-byte into ordered 2 MiB parts;
- parts are stored under `chunks/<logical-path>/<index>.part`;
- the untouched original is stored under `fallback/<logical-path>`;
- `asset-manifest.json` describes the chunked logical files.

The manifest has `formatVersion: 2`:

```json
{
  "formatVersion": 2,
  "chunkSizeBytes": 2097152,
  "assets": {
    "Build/game.data.br": {
      "encoding": "br",
      "sizeBytes": 18432000,
      "sha256": "<lowercase SHA-256>",
      "fallbackUrl": "fallback/Build/game.data.br",
      "chunks": [
        {
          "url": "chunks/Build/game.data.br/00000.part",
          "sizeBytes": 2097152,
          "sha256": "<lowercase SHA-256>"
        }
      ]
    }
  }
}
```

Chunks from a `.br` file do not receive `Content-Encoding: br`: an individual
part is not a complete Brotli stream. The user frontend checks each part,
concatenates the original compressed bytes, checks the complete SHA-256, then
decompresses once before initializing Unity. After three failures for one
part, it uses the corresponding full object under `fallback/`.

The browser may cache every successful immutable part independently. Retrying
an interrupted load therefore normally downloads only the missing parts.

## Publish Request

```http
POST /api/admin/game/assets
Content-Type: multipart/form-data
X-XSRF-TOKEN: <csrf-token>
```

Multipart fields:

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `version` | string | yes | New cache-busting game version |
| `files` | file[] | yes | Repeated field, one part per file |

JavaScript example:

```js
async function publishGameAssets({ version, files, csrfToken }) {
  const formData = new FormData();
  formData.append("version", version);

  for (const file of files) {
    formData.append("files", file, file.webkitRelativePath || file.name);
  }

  const response = await fetch("/api/admin/game/assets", {
    method: "POST",
    credentials: "include",
    headers: {
      "X-XSRF-TOKEN": csrfToken
    },
    body: formData
  });

  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message || "Publication impossible.");
  }

  return response.json();
}
```

After success, call `GET /api/public/game` again and update the admin panel with the returned active release.

## Upload Progress

`fetch` does not expose upload progress in browsers. Use `XMLHttpRequest` if the panel needs a progress bar:

```js
function publishGameAssetsWithProgress({ version, files, csrfToken, onProgress }) {
  return new Promise((resolve, reject) => {
    const formData = new FormData();
    formData.append("version", version);

    for (const file of files) {
      formData.append("files", file, file.webkitRelativePath || file.name);
    }

    const request = new XMLHttpRequest();
    request.open("POST", "/api/admin/game/assets");
    request.withCredentials = true;
    request.setRequestHeader("X-XSRF-TOKEN", csrfToken);

    request.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        onProgress?.(Math.round((event.loaded / event.total) * 100));
      }
    };

    request.onload = () => {
      const body = request.responseText ? JSON.parse(request.responseText) : null;
      if (request.status >= 200 && request.status < 300) {
        resolve(body);
        return;
      }
      reject(new Error(body?.message || "Publication impossible."));
    };

    request.onerror = () => reject(new Error("Upload interrompu."));
    request.send(formData);
  });
}
```

## Recommended UX

- Disable the publish button while the upload is running.
- Show a confirmation before publishing: `Cette publication remplacera la version active. L'ancienne version sera supprimee apres bascule.`
- Validate that `version` is not empty before sending.
- Validate that at least one file is selected before sending.
- Display the selected folder file count and total size before publication.
- On success, show the new `version`, `baseUrl`, and `publishedAt`.
- On failure, keep the selected files and version in the form so the admin can retry.

## Backend Behavior

Publication steps:

1. Validate the version.
2. Validate the required Unity files.
3. Upload small files directly and large files as chunks plus fallback under `game/{version}/`.
4. Upload `asset-manifest.json`.
5. Store the active release metadata in the database.
6. Delete the previous `game/{oldVersion}/` prefix from S3.

The old version cleanup is non-blocking after the active release has switched. If S3 cleanup fails, the new version remains active and the backend logs a warning.
If publication fails before activation, the incomplete new prefix is deleted
in best effort and the previous release remains active.

Uploaded files receive:

```http
Cache-Control: public, max-age=31536000, immutable
```

Complete files ending in `.br`, including objects under `fallback/`, also receive:

```http
Content-Encoding: br
```

## Errors To Handle

| Status | Meaning | Recommended UI |
| --- | --- | --- |
| `400` | Invalid version, missing Unity files, empty folder, duplicate path, same active version | Show backend message |
| `401` | Admin not authenticated | Redirect to login/session recovery |
| `403` | Missing `ADMIN` role or CSRF token | Show permission/session error |
| `413` | Multipart body too large | Ask ops to raise multipart/proxy limits |
| `5xx` | S3, database, or server failure | Show retry option |

## Deployment Note

Unity WebGL builds can exceed the default multipart limits.

Set these environment variables high enough for the largest single file and full build folder:

```bash
SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=300MB
SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE=700MB
SERVER_TOMCAT_MAX_PART_COUNT=1000
```

`SERVER_TOMCAT_MAX_PART_COUNT` must be higher than the number of selected files plus form fields. For example, a 308-file build sends at least 309 multipart parts because the `version` field is one part too.

Also check reverse proxy limits, such as Nginx `client_max_body_size`, if the admin panel uploads through a proxy.
