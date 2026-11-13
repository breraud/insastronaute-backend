# Game Frontend Integration

This guide explains how the user-facing frontend should load the active Unity WebGL game build published by the backend.

## Goal

The frontend must not hardcode S3 game URLs.

Always ask the API for the active game release:

```http
GET /api/public/game
```

Then use the returned versioned URLs to load the game.

## API Response

```json
{
  "version": "2026.1.0",
  "baseUrl": "https://s3.example.com/insastronaute-public/game/2026.1.0/",
  "indexUrl": "https://s3.example.com/insastronaute-public/game/2026.1.0/index.html",
  "loaderUrl": "https://s3.example.com/insastronaute-public/game/2026.1.0/Build/game.loader.js",
  "fileCount": 308,
  "totalSizeBytes": 42000000,
  "publishedAt": "2026-07-01T10:00:00Z"
}
```

Important fields:

| Field | Use |
| --- | --- |
| `version` | Display/debug current game version |
| `baseUrl` | Root URL for all Unity build assets |
| `indexUrl` | Direct URL to the full Unity HTML page |
| `loaderUrl` | Unity loader script URL |
| `publishedAt` | Optional display/debug metadata |

## Recommended Integration

The simplest and most robust integration is to embed the published Unity `index.html` in an iframe.

```html
<iframe
  id="game-frame"
  title="INSAstronaute game"
  allow="fullscreen"
></iframe>
```

```js
async function loadGameFrame() {
  const response = await fetch("/api/public/game", {
    credentials: "include"
  });

  if (!response.ok) {
    throw new Error("Game is not available.");
  }

  const release = await response.json();
  const frame = document.getElementById("game-frame");
  frame.src = release.indexUrl;
}
```

Why this is recommended:

- Unity keeps control of its generated `index.html`.
- `TemplateData`, `Build`, `StreamingAssets`, service worker, and manifest references stay consistent.
- Frontend code does not need to duplicate Unity loader configuration.
- Updating the game only requires publishing a new backend version.

## React Example

```tsx
import { useEffect, useState } from "react";

type GameRelease = {
  version: string;
  baseUrl: string;
  indexUrl: string;
  loaderUrl: string;
  fileCount: number;
  totalSizeBytes: number;
  publishedAt: string;
};

export function GamePage() {
  const [release, setRelease] = useState<GameRelease | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadRelease() {
      try {
        const response = await fetch("/api/public/game", {
          credentials: "include"
        });

        if (!response.ok) {
          throw new Error("Le jeu n'est pas disponible.");
        }

        const data = await response.json();
        if (!cancelled) {
          setRelease(data);
        }
      } catch (error) {
        if (!cancelled) {
          setError(error instanceof Error ? error.message : "Erreur de chargement.");
        }
      }
    }

    loadRelease();

    return () => {
      cancelled = true;
    };
  }, []);

  if (error) {
    return <p>{error}</p>;
  }

  if (!release) {
    return <p>Chargement du jeu...</p>;
  }

  return (
    <iframe
      title="INSAstronaute"
      src={release.indexUrl}
      allow="fullscreen"
      style={{
        width: "100%",
        height: "100%",
        border: 0
      }}
    />
  );
}
```

Make sure the parent layout gives the iframe a real height:

```css
.game-page {
  min-height: 100dvh;
}

.game-page iframe {
  display: block;
  width: 100%;
  height: 100dvh;
  border: 0;
}
```

## Manual Unity Loader Integration

Use this only if the frontend must render its own shell around the Unity canvas.

Load the release first:

```js
const release = await fetch("/api/public/game").then((response) => {
  if (!response.ok) {
    throw new Error("Game is not available.");
  }
  return response.json();
});
```

Then inject the Unity loader:

```js
function loadScript(src) {
  return new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = src;
    script.onload = resolve;
    script.onerror = reject;
    document.body.appendChild(script);
  });
}

await loadScript(release.loaderUrl);
```

Then call Unity with URLs rooted on `baseUrl`:

```js
await createUnityInstance(document.querySelector("#unity-canvas"), {
  dataUrl: release.baseUrl + "Build/game.data.br",
  frameworkUrl: release.baseUrl + "Build/game.framework.js.br",
  codeUrl: release.baseUrl + "Build/game.wasm.br",
  streamingAssetsUrl: release.baseUrl + "StreamingAssets",
  companyName: "INSAstronaute",
  productName: "INSAstronaute",
  productVersion: release.version
});
```

Only use this approach if the Unity exported `index.html` is not suitable.

## Cache Behavior

Game files are stored under:

```text
game/{version}/
```

Example:

```text
game/2026.1.0/Build/game.wasm.br
game/2026.1.1/Build/game.wasm.br
```

Files are served with immutable cache headers. This is intentional.

When a new version is published:

1. `GET /api/public/game` returns a new `baseUrl`.
2. The browser requests different versioned asset URLs.
3. Old cached files do not conflict with the new game.

Do not add random query parameters to Unity asset URLs. The versioned path already handles cache invalidation.

## Service Worker Warning

The Unity build contains `ServiceWorker.js`.

If the game is embedded with `indexUrl` from S3, the service worker scope is isolated to the S3 game path.

If the frontend copies Unity files into the app origin or proxies `index.html`, review service worker registration carefully. A Unity service worker registered on the main app origin can interfere with app routing and caching.

Recommended default: iframe the S3 `indexUrl`.

## Error States

Handle these states explicitly:

| Situation | UI |
| --- | --- |
| `GET /api/public/game` returns `404` | Show `Le jeu n'est pas encore disponible.` |
| Network failure | Show retry action |
| Iframe load failure | Show retry action and report the current `version` |
| Mobile viewport too small | Show responsive fullscreen container |

The public endpoint is unauthenticated, but using `credentials: "include"` is fine and keeps the frontend fetch style consistent with the rest of the app.

## Minimal Checklist

- Call `GET /api/public/game` on page load.
- Use `indexUrl` in an iframe by default.
- Do not hardcode S3 URLs.
- Do not cache the API response forever; refetch when entering the game page.
- Make the iframe/container fullscreen or give it a stable explicit height.
- Display a clean unavailable state when no game release exists.
- Keep the current `version` available in logs or debug UI.
