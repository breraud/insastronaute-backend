# Game Configs Frontend Flow

Game configs are JSON files stored in the public S3 bucket.
The admin API edits the mutable current folder and publishes immutable versioned releases from it.

## S3 Layout

Default prefixes:

- Current editable configs: `GameData/current/dev/`
- Versioned releases: `GameData/versions/{version}/`
- Latest release marker: `GameData/versions/latest.json`

These prefixes are configurable with:

- `APP_GAME_CONFIG_OBJECT_PREFIX`
- `APP_GAME_CONFIG_RELEASES_OBJECT_PREFIX`

The API accepts JSON file paths under the current prefix.
Each folder segment must match `^[A-Za-z0-9_-]+$`, and the final file name must match `^[A-Za-z0-9_-]+\.json$`.

## Admin Screen Flow

1. Load the current editable file list:

   ```http
   GET /api/admin/game/configs
   ```

   Response:

   ```json
   ["cards.json", "levels/world_1/settings.json"]
   ```

2. Load the current folder list:

   ```http
   GET /api/admin/game/configs/folders
   ```

   Response:

   ```json
   ["levels", "levels/world_1"]
   ```

3. Create a folder in current:

   ```http
   POST /api/admin/game/configs/folders?path=levels/world_2
   X-XSRF-TOKEN: <csrf-token>
   ```

   S3 folders are virtual; this creates an empty folder marker at `GameData/current/dev/levels/world_2/`.
   Creating a JSON file under a folder also makes that folder exist from S3's point of view.

4. Load one editable file when selected:

   ```http
   GET /api/admin/game/configs/levels/world_1/settings.json
   ```

   Response body is raw JSON text.

5. Edit JSON in the frontend.

   The frontend should parse locally before saving so admins get immediate feedback, but the backend is the source of truth and rejects invalid JSON with `400 Bad Request`.

6. Save to current:

   ```http
   PUT /api/admin/game/configs/levels/world_1/settings.json
   Content-Type: application/json
   X-XSRF-TOKEN: <csrf-token>

   {"enabled":true}
   ```

   Success returns `204 No Content`.
   This creates or overwrites `GameData/current/dev/levels/world_1/settings.json`.

7. Delete a current file:

   ```http
   DELETE /api/admin/game/configs/levels/world_1/settings.json
   X-XSRF-TOKEN: <csrf-token>
   ```

   Success returns `204 No Content`.
   This deletes only the targeted JSON object.

8. Delete a current folder:

   ```http
   DELETE /api/admin/game/configs/folders?path=levels/world_1
   X-XSRF-TOKEN: <csrf-token>
   ```

   Success returns `204 No Content`.
   This deletes the folder marker and every nested object under `GameData/current/dev/levels/world_1/`.

9. Release current configs:

   ```http
   POST /api/admin/game/configs/releases?version=2026.1.0
   X-XSRF-TOKEN: <csrf-token>
   ```

   The backend copies every valid current `*.json` file, including nested files, into `GameData/versions/2026.1.0/` and updates `GameData/versions/latest.json`.

   Response:

   ```json
   {
     "version": "2026.1.0",
     "objectPrefix": "GameData/versions/2026.1.0/",
     "files": ["cards.json", "levels/world_1/settings.json"],
     "releasedAt": "2026-07-09T10:00:00Z"
   }
   ```

10. Upload and release a JSON folder directly:

   If the admin already has a local config folder and wants to publish it without editing files one by one, upload the folder directly as a release:

   ```http
   POST /api/admin/game/configs/releases/upload?version=2026.1.0
   Content-Type: multipart/form-data
   X-XSRF-TOKEN: <csrf-token>
   ```

   Multipart fields:

   | Field | Type | Required | Notes |
   | --- | --- | --- | --- |
   | `version` | string | yes | Letters, digits, `.`, `_`, `-`; must start with a letter or digit |
   | `files` | file[] | yes | Repeated field, one part per JSON file |

   Browser folder picker example:

   ```html
   <input type="file" webkitdirectory multiple />
   ```

   ```js
   async function uploadAndReleaseGameConfigs({ version, files, csrfToken }) {
     const formData = new FormData();
     formData.append("version", version);
     for (const file of files) {
       formData.append("files", file, file.webkitRelativePath || file.name);
     }

     const response = await fetch("/api/admin/game/configs/releases/upload", {
       method: "POST",
       headers: { "X-XSRF-TOKEN": csrfToken },
       body: formData
     });

     if (!response.ok) {
       throw new Error(await response.text());
     }

     return response.json();
   }
   ```

   The backend validates every uploaded file path with the same JSON path rules as current configs, rejects duplicate relative paths, rejects invalid JSON, uploads the files to `GameData/versions/{version}/`, and updates `GameData/versions/latest.json`.
   This route does not modify `GameData/current/dev/`.

11. Load existing releases:

   ```http
   GET /api/admin/game/configs/releases
   ```

   Response:

   ```json
   [
     {
       "version": "2026.1.0",
       "objectPrefix": "GameData/versions/2026.1.0/",
       "files": ["cards.json", "levels/world_1/settings.json"],
       "releasedAt": "2026-07-09T10:00:00Z"
     },
     {
       "version": "2026.0.0",
       "objectPrefix": "GameData/versions/2026.0.0/",
       "files": ["cards.json"],
       "releasedAt": "2026-07-08T10:00:00Z"
     }
   ]
   ```

   Use this for the release selector/history panel.
   The backend returns only valid version folders and valid JSON files under the releases prefix.

12. Load one file from a release when selected:

   ```http
   GET /api/admin/game/configs/releases/2026.1.0/files/levels/world_1/settings.json
   ```

   Response body is raw JSON text.
   Use this for read-only preview or diff views.
   Released files are immutable from the API; to change a config, copy or recreate the content in current and publish a new version.

13. Show the latest release:

   ```http
   GET /api/admin/game/configs/releases/latest
   ```

   Use this to show which config version is currently published.

14. Delete a release:

   ```http
   DELETE /api/admin/game/configs/releases/2026.1.0
   X-XSRF-TOKEN: <csrf-token>
   ```

   Success returns `204 No Content`.
   If the deleted version is not the latest release, the latest marker is unchanged.
   If the deleted version is the latest release, the backend promotes the newest remaining release into `GameData/versions/latest.json`.
   If no release remains, the backend deletes `GameData/versions/latest.json`.

   After deleting a release, reload both:

   ```http
   GET /api/admin/game/configs/releases
   GET /api/admin/game/configs/releases/latest
   ```

   If the latest endpoint returns `404 Not Found`, show that no release is currently published.

## Frontend Notes

- Fetch `GET /api/csrf` before `PUT`, `POST`, or `DELETE` if the app does not already have an `XSRF-TOKEN` cookie.
- Treat `400` on save, folder creation, or delete as an invalid path, invalid file name, or invalid JSON.
- Treat `400` on direct upload as an invalid version, invalid/duplicate file path, empty folder, or invalid JSON file.
- Treat `404` on latest release as “no release published”.
- Treat `404` on release file read as “file or release no longer exists”.
- Treat `404` on folder delete as “folder does not exist”.
- Version names may contain letters, digits, `.`, `_`, and `-`, and must start with a letter or digit.
- Do not allow editing release files directly. All writes go through current, then release snapshots current.
- After releasing, direct uploading, or deleting a release, refresh the release list and latest release state before updating the UI.
