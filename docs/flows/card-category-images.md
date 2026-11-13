# Card Category Image Flow

This document describes how admins manage card category images and how clients consume them.

## Purpose

Card category images are public visuals attached to `CardCategory` records. They are used by deck and card UIs to display category-level artwork separately from individual card images.
Categories also carry `hidden` and `game` flags. Both remain manageable through admin endpoints and are omitted from normal player deck and public deck detail responses. Game-category cards are available to STS through `GET /api/sts/cardsImage`; hidden-category cards are not. These are API-level visibility rules and do not revoke already-known public S3 asset URLs.

The API stores category images in the public S3 bucket under:

```text
card-categories/{categoryId}/{version}/original.jpg
card-categories/{categoryId}/{version}/thumbnail.jpg
```

The API returns browser-ready `imageUrl` and `thumbnailUrl` values. Frontends should use those URLs directly.

## Admin Flow

1. Create the card category with `POST /api/admin/card-categories`.
2. Upload or replace its image with `PATCH /api/admin/card-categories/{categoryId}/image`.
3. Use `GET /api/admin/card-categories` to list categories and verify the returned URLs.
4. Delete the image with `DELETE /api/admin/card-categories/{categoryId}/image` if the category should no longer expose artwork.

All state-changing browser requests must include:

```http
X-XSRF-TOKEN: <token from GET /api/csrf>
```

## Create A Category

```bash
curl -X POST "$API_BASE_URL/api/admin/card-categories" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -b "$COOKIE_JAR" \
  -d '{
    "name": "Rare",
    "displayOrder": 10,
    "hidden": false,
    "game": false
  }'
```

Example response:

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "Rare",
  "displayOrder": 10,
  "imageUrl": null,
  "thumbnailUrl": null,
  "contentType": null,
  "hidden": false,
  "game": false
}
```

Use `"hidden": true` for an admin-only category. Use `"game": true` for a category consumed by STS but hidden from normal player card/deck views. Both types are still returned by `GET /api/admin/card-categories`.

## Upload Or Replace The Image

```bash
curl -X PATCH "$API_BASE_URL/api/admin/card-categories/$CATEGORY_ID/image" \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -b "$COOKIE_JAR" \
  -F "image=@./rare.png;type=image/png"
```

The API:

- accepts JPEG and PNG uploads only;
- rejects images larger than 20 MB;
- compresses the display image to JPEG;
- generates a JPEG thumbnail;
- uploads both objects to the public S3 bucket with public read ACL;
- deletes the previous image objects after the category is saved.

Example response:

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "Rare",
  "displayOrder": 10,
  "imageUrl": "https://s3.example.com/insastronaute-public/card-categories/11111111-1111-1111-1111-111111111111/version/original.jpg",
  "thumbnailUrl": "https://s3.example.com/insastronaute-public/card-categories/11111111-1111-1111-1111-111111111111/version/thumbnail.jpg",
  "contentType": "image/jpeg",
  "hidden": false,
  "game": false
}
```

## Consume Category Images

Admin screens can read category image URLs from:

```http
GET /api/admin/card-categories
```

Player card deck screens receive category image URLs from:

```http
GET /api/cards/deck?collectionType=VIRTUAL
GET /api/cards/deck?collectionType=PHYSICAL
```

Hidden and game categories are omitted from these player responses.

Deck detail screens receive the category images used by the deck from:

```http
GET /api/decks/{deckId}
```

Hidden and game categories are omitted from public deck detail responses even when a deck contains cards from those categories.

Use `thumbnailUrl` for compact lists and `imageUrl` for larger displays.

## Delete The Image

```bash
curl -X DELETE "$API_BASE_URL/api/admin/card-categories/$CATEGORY_ID/image" \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -b "$COOKIE_JAR"
```

The response keeps the category but clears its image fields:

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "Rare",
  "displayOrder": 10,
  "imageUrl": null,
  "thumbnailUrl": null,
  "contentType": null,
  "hidden": false,
  "game": false
}
```

## Configuration Notes

Category images use the public S3 bucket configured by:

```text
S3_PUBLIC_ENDPOINT
S3_PUBLIC_BUCKET
```

Public bucket initialization allows anonymous reads for `card-categories/*`. If bucket policies are managed outside the API, make sure `card-categories/*` objects are readable by browsers.
