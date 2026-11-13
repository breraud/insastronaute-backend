# Admin Articles

This document describes the admin frontend integration for WYSIWYG articles and event pages.

The backend flow is editor-first:

1. Create an empty article.
2. Upload images or videos with the returned article id.
3. Upload a cover image to S3, or set an external cover URL.
4. Insert returned public media URLs into the editor content.
5. Auto-save title, HTML, cover URL, and category whenever the editor state changes.
6. Delete abandoned articles from the editor UI when needed; the backend removes the article media directory from S3.

Article management routes are under `/api/admin/articles`; categories are listed from `/api/categories` and managed from `/api/admin/categories`. Article management routes require an admin session. Browser state-changing requests must include the CSRF header.

## CSRF

Fetch a token before the first write request:

```http
GET /api/csrf
```

Send the returned token on `POST` and `PUT` requests:

```http
X-XSRF-TOKEN: <token>
```

When using `fetch`, keep cookies enabled:

```ts
const response = await fetch("/api/admin/articles", {
  method: "POST",
  credentials: "include",
  headers: {
    "X-XSRF-TOKEN": csrfToken,
  },
});
```

## 1. List Articles

Load drafts and published articles for the admin article list.

```http
GET /api/admin/articles
```

Response `200 OK`:

```json
[
  {
    "id": "6dbfd8d7-3ef7-4d3c-9ad5-17fb3e6925af",
    "title": "Programme de la semaine",
    "coverImageUrl": "https://cdn.example.com/articles/programme-cover.jpg",
    "status": "WRITING",
    "category": {
      "id": "c9acdbad-9e7f-43db-90ea-35a98ded0d7d",
      "name": "Vie associative",
      "slug": "vie-associative"
    },
    "createdAt": "2026-07-02T08:00:00Z",
    "updatedAt": "2026-07-02T08:10:00Z"
  }
]
```

## 2. Get Article

Open an existing draft or published article in the editor with the full saved HTML body.

```http
GET /api/admin/articles/{articleId}
```

Response `200 OK`:

```json
{
  "id": "6dbfd8d7-3ef7-4d3c-9ad5-17fb3e6925af",
  "title": "Programme de la semaine",
  "contentHtml": "<h2>Programme</h2><p>...</p>",
  "coverImageUrl": "https://cdn.example.com/articles/programme-cover.jpg",
  "status": "WRITING",
  "category": {
    "id": "c9acdbad-9e7f-43db-90ea-35a98ded0d7d",
    "name": "Vie associative",
    "slug": "vie-associative"
  },
  "createdAt": "2026-07-02T08:00:00Z",
  "updatedAt": "2026-07-02T08:10:00Z"
}
```

## 3. Create Article

Create the article before opening the media upload flow.

```http
POST /api/admin/articles
```

Response `201 Created`:

```json
{
  "id": "6dbfd8d7-3ef7-4d3c-9ad5-17fb3e6925af"
}
```

Store `id` in the admin editor state. Every media upload for this article must use this id.

## 4. Upload Editor Media

Upload one file at a time when the editor requests an image/video insertion.

```http
POST /api/admin/articles/{articleId}/media
Content-Type: multipart/form-data
```

Form fields:

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `file` | File | yes | JPEG, PNG, WebP, MP4, or WebM selected by the admin user. Maximum size: 15 MB. |

Response `201 Created`:

```json
{
  "id": "5d8e03ed-1ee1-4795-88aa-c7810c67ee35",
  "url": "https://s3.example.com/insastronaute-public/articles/6dbfd8d7-3ef7-4d3c-9ad5-17fb3e6925af/2c6f8c20-feb4-4ef5-99b6-5c65db78e7b3-banner.jpg"
}
```

The backend stores the object under:

```text
articles/{articleId}/{generatedId}-{filename}
```

The backend detects the real media type before upload. The file extension must match the detected format; the client-provided `Content-Type` is not trusted for storage.

Use the returned `url` directly in TipTap/Quill:

```ts
async function uploadArticleMedia(articleId: string, file: File, csrfToken: string): Promise<string> {
  const formData = new FormData();
  formData.append("file", file);

  const response = await fetch(`/api/admin/articles/${articleId}/media`, {
    method: "POST",
    credentials: "include",
    headers: {
      "X-XSRF-TOKEN": csrfToken,
    },
    body: formData,
  });

  if (!response.ok) {
    throw new Error("Media upload failed");
  }

  const payload: { id: string; url: string } = await response.json();
  return payload.url;
}
```

For TipTap image insertion:

```ts
const url = await uploadArticleMedia(articleId, file, csrfToken);
editor.chain().focus().setImage({ src: url }).run();
```

For Quill image insertion:

```ts
const url = await uploadArticleMedia(articleId, file, csrfToken);
const range = quill.getSelection(true);
quill.insertEmbed(range.index, "image", url);
```

For videos, insert the generated `<video>` or iframe embed supported by your editor.

## 5. Upload Article Cover

Use this route when the admin selects a local cover image. The backend stores the image under the article S3 directory, updates `coverImageUrl`, and deletes the previous managed cover object if there was one.

```http
POST /api/admin/articles/{articleId}/cover
Content-Type: multipart/form-data
```

Form fields:

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `file` | File | yes | JPEG, PNG, or WebP selected by the admin user. Maximum size: 15 MB. |

Response `200 OK`:

```json
{
  "id": "6dbfd8d7-3ef7-4d3c-9ad5-17fb3e6925af",
  "title": "Programme de la semaine",
  "contentHtml": "<h2>Programme</h2><p>...</p>",
  "coverImageUrl": "https://s3.example.com/insastronaute-public/articles/6dbfd8d7-3ef7-4d3c-9ad5-17fb3e6925af/cover/2c6f8c20-feb4-4ef5-99b6-5c65db78e7b3-cover.jpg",
  "status": "WRITING",
  "category": null,
  "createdAt": "2026-07-02T08:00:00Z",
  "updatedAt": "2026-07-02T08:10:00Z"
}
```

The backend stores uploaded covers under:

```text
articles/{articleId}/cover/{generatedId}-{filename}
```

To use an external cover image instead, send its HTTP or HTTPS URL in `coverImageUrl` on the auto-save endpoint. If the previous cover was an uploaded S3 cover, the backend deletes the old object when the URL changes.

## 6. List Article Media

Use this route to rebuild the media panel, debug editor state, or show files already uploaded for a draft.

```http
GET /api/admin/articles/{articleId}/media
```

Response `200 OK`:

```json
[
  {
    "id": "5d8e03ed-1ee1-4795-88aa-c7810c67ee35",
    "articleId": "6dbfd8d7-3ef7-4d3c-9ad5-17fb3e6925af",
    "s3Url": "https://s3.example.com/insastronaute-public/articles/6dbfd8d7-3ef7-4d3c-9ad5-17fb3e6925af/2c6f8c20-feb4-4ef5-99b6-5c65db78e7b3-banner.jpg",
    "fileType": "image/jpeg",
    "originalFileName": "banner.jpg",
    "createdAt": "2026-07-02T08:02:00Z"
  }
]
```

## 7. List Categories

Fetch available categories for the editor.

```http
GET /api/categories
```

Response `200 OK`:

```json
[
  {
    "id": "c9acdbad-9e7f-43db-90ea-35a98ded0d7d",
    "name": "Vie associative",
    "slug": "vie-associative"
  }
]
```

Admin category CRUD is available under `/api/admin/categories`:

```http
GET /api/admin/categories
POST /api/admin/categories
PUT /api/admin/categories/{id}
DELETE /api/admin/categories/{id}
```

Create/update request:

```json
{
  "name": "Vie associative",
  "slug": "vie-associative"
}
```

`slug` may be omitted; the backend generates it from `name`.

## 8. Auto-Save Article

Send the current editor HTML, title, and selected category id.

```http
PUT /api/admin/articles/{articleId}
Content-Type: application/json
```

Request:

```json
{
  "title": "Programme de la semaine",
  "contentHtml": "<h2 class=\"text-xl\" style=\"font-weight: 700\">Programme</h2><p>...</p>",
  "coverImageUrl": "https://cdn.example.com/articles/programme-cover.jpg",
  "categoryId": "c9acdbad-9e7f-43db-90ea-35a98ded0d7d"
}
```

Response `200 OK`:

```json
{
  "id": "6dbfd8d7-3ef7-4d3c-9ad5-17fb3e6925af",
  "title": "Programme de la semaine",
  "contentHtml": "<h2 class=\"text-xl\" style=\"font-weight: 700\">Programme</h2><p>...</p>",
  "coverImageUrl": "https://cdn.example.com/articles/programme-cover.jpg",
  "status": "WRITING",
  "category": {
    "id": "c9acdbad-9e7f-43db-90ea-35a98ded0d7d",
    "name": "Vie associative",
    "slug": "vie-associative"
  },
  "createdAt": "2026-07-02T08:00:00Z",
  "updatedAt": "2026-07-02T08:10:00Z"
}
```

The backend sanitizes `contentHtml` with Jsoup before saving. The sanitizer removes unsafe scripts and event handlers, preserves Tailwind/editor `class` attributes, keeps only a small safe subset of inline style properties, removes `data:` images, and only keeps iframe embeds from YouTube, YouTube NoCookie, or Vimeo.

## 9. Delete Article

Delete an article and its S3 media directory:

```http
DELETE /api/admin/articles/{articleId}
```

Response `204 No Content`.

## Error Handling

The API returns the common error envelope:

```json
{
  "timestamp": "2026-07-02T08:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Article introuvable.",
  "path": "/api/admin/articles/6dbfd8d7-3ef7-4d3c-9ad5-17fb3e6925af/media",
  "fieldErrors": []
}
```

Common cases:

| Status | Case | Frontend behavior |
| --- | --- | --- |
| `400` | Missing file, invalid body, category still used, upload/storage failure | Show a blocking form/upload error and let the admin retry. |
| `401` | Session expired | Redirect to login or refresh session state. |
| `403` | Missing CSRF token or insufficient access | Refresh CSRF token, then retry once if appropriate. |
| `404` | Article id or category id does not exist | Stop editing this draft and show a not-found state. |

## Recommended Admin UI State

Keep these fields in the editor state:

```ts
type ArticleEditorState = {
  articleId: string;
  title: string;
  contentHtml: string;
  coverImageUrl: string | null;
  status: "WRITING" | "PUBLISHED";
  category: ArticleCategory | null;
  media: ArticleMedia[];
  saving: boolean;
  uploadingCover: boolean;
  uploadingMedia: boolean;
};

type ArticleMedia = {
  id: string;
  articleId: string;
  s3Url: string;
  fileType: string;
  originalFileName: string;
  createdAt: string;
};

type ArticleCategory = {
  id: string;
  name: string;
  slug: string;
};
```

Create the article before allowing media insertion. If an admin opens the editor without an `articleId`, call `POST /api/admin/articles` first, then mount the WYSIWYG editor.

## Integration Notes

- Do not base64-embed files into `contentHtml`; always upload and insert the returned public URL.
- For covers, either use `POST /api/admin/articles/{articleId}/cover` and keep the returned `coverImageUrl`, or send an external URL through auto-save.
- Keep the exact HTML generated by the editor; the backend performs final sanitization.
- The sanitizer preserves Tailwind/editor `class` attributes and safe TipTap-style inline formatting such as `text-align`, color, font weight, italic, and underline/strike.
- The backend deletes uploaded article files from S3 when they no longer exist in `article_media`; the scheduled cleanup runs daily by default.
- `PUT /api/admin/articles/{articleId}` is an auto-save endpoint and does not change the article status.
- Changing `coverImageUrl` away from an uploaded S3 cover deletes the previous managed cover object on a best-effort basis.
- `DELETE /api/admin/articles/{articleId}` deletes the database row and triggers best-effort deletion of `articles/{articleId}/` from S3.
