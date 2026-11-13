# Card Management Flow

This document describes the collectible card flow for admins and users.

## Concepts

- A card is the configured collectible item: name, category id, enabled flag, display order, source image, and generated thumbnail.
- A card category is an admin-managed record. Collectible card create/update requests must reference an existing category by `categoryId` or existing category name.
- A card category has independent `hidden` and `game` flags. Hidden categories are admin-only. Game categories are hidden from normal player card/deck screens but exposed to the STS client through `GET /api/sts/cardsImage`.
- Hiding is API-level visibility. It does not revoke already-known public S3 asset URLs.
- A virtual collection entry links one user to one card and stores the owned quantity.
- A physical card is a unique `CardInstance` with one public claim code and at most one current owner.
- The default card is only the back cover asset used for undiscovered cards. It has the fixed id `00000000-0000-0000-0000-000000000000`.
- Only the fixed default card can have `defaultCard=true`.
- User deck reads are split by `collectionType`: `VIRTUAL` is quantity-based, while `PHYSICAL` is derived from assigned physical instances.

## Lifecycle Summary

1. Admin creates categories, optionally marking some as hidden or game-only.
2. Admin uploads or replaces the hidden back cover with `PATCH /api/admin/cards/cover`.
3. Admin creates collectible cards with an image, a category, an enabled state, and an order.
4. Admin grants virtual cards to users through collection quantities.
5. Physical cards are created as unique instances, usually by print batch generation, direct admin creation, then assigned by admin or claimed by players.
6. User reads a deck with `GET /api/cards/deck?collectionType=VIRTUAL|PHYSICAL`.
7. Owned cards return their real metadata and owned quantity.
8. Locked cards return the cover artwork and `???` string fields, with `quantity: 0`.
9. Admin can later reorder cards, toggle category visibility, disable cards, replace images, grant more virtual copies, or delete non-default cards.

## Admin Flow

1. Create card categories first with `POST /api/admin/card-categories`.
2. Create or replace the default back cover image with `PATCH /api/admin/cards/cover`.
3. Do not manage the default card like a collectible card. The API rejects metadata changes, disabling, deleting, granting, or removing its default status.
4. Create collection cards with `POST /api/admin/cards`, including their image, category id, enabled state, and display order.
5. Update collection card metadata or image with `PATCH /api/admin/cards/{id}`.
6. Replace only a card image with `PATCH /api/admin/cards/{id}/image`.
7. Reorder cards inside a category with `PUT /api/admin/cards/categories/{categoryId}/order`.
8. Mark a category `hidden` when every card in it must remain admin-only, or `game` when it should be available to STS but absent from normal player card/deck screens.
9. Disable a card when only that card should disappear from user decks without deleting collection history.
10. Delete a non-default card only when it should be removed from configuration and user collections.
11. Grant a virtual card to a user with `POST /api/admin/cards/{id}/collections?collectionType=VIRTUAL` and a JSON body containing `userId` and optional `quantity`. The default collection type is `VIRTUAL`.
12. Manage physical ownership through `CardInstance` endpoints, not collection grants.

## User Flow

1. The user opens their deck with `GET /api/cards/deck?collectionType=VIRTUAL|PHYSICAL`. The default collection type is `VIRTUAL`.
2. The API returns enabled collectible cards grouped by category. Hidden categories, game categories, and the default card itself are not returned as deck cards.
3. Owned cards have `quantity > 0`, their real strings, and their real image URLs.
4. Undiscovered cards return the default card image URLs as the visual back cover and `???` for string fields such as `name` and `category`.
5. When an admin grants a virtual card or assigns a physical instance to the user, the matching deck response increases `quantity` and returns its real strings and image, unless the card's category is hidden or game-only.

## Frontend Notes

- Use `quantity === 0` for locked cards and `quantity > 0` for owned cards.
- Use `imageUrl` or `thumbnailUrl` directly for the displayed image; locked cards already receive the default card artwork from the API and placeholder strings.
- Do not render hidden or game categories in normal player card screens. They are absent from `GET /api/cards/deck`.
- The STS frontend should load card artwork from `GET /api/sts/cardsImage`. It returns public and game-category cards, excludes hidden categories, and uses the same `CardDto[]` response shape as `GET /api/admin/cards`.
- Do not special-case a missing image for locked cards.
- Use `PATCH /api/admin/cards/cover` for changing the back cover image. Admin UI does not need to expose the hidden cover card as a normal card.
- Use `DELETE /api/admin/cards/cover` to remove the back cover. Locked cards then keep placeholder strings and return `null` image URLs until a new cover is uploaded.

## Request Guide

All state-changing browser requests must include the CSRF header:

```http
X-XSRF-TOKEN: <token from GET /api/csrf>
```

### Create A Category

Categories are explicit records. Create them before creating cards.

```bash
curl -X POST "$API_BASE_URL/api/admin/card-categories" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -b "$COOKIE_JAR" \
  -d '{
    "name": "Legendary",
    "displayOrder": 1,
    "hidden": false,
    "game": false
  }'
```

Use the returned `id` as `categoryId` when creating or updating cards.

Set `"hidden": true` to make a category admin-only. Set `"game": true` to hide it from normal player screens while including it in the STS card-image catalogue. Both flags default to `false` on creation; omitting either flag on `PUT /api/admin/card-categories/{id}` preserves its current value.

### Read STS Card Images

```bash
curl "$API_BASE_URL/api/sts/cardsImage" \
  -b "$COOKIE_JAR"
```

The endpoint requires authentication. It returns the same array of card objects as `GET /api/admin/cards`, except the default cover and cards in hidden categories are omitted. Public and game-category cards are included; disabled cards remain included to match the admin catalogue contract.

### Replace The Default Back Cover Image

The cover route accepts only an image. The API creates the hidden cover card if it does not exist yet, or replaces its image if it already exists.

```bash
curl -X PATCH "$API_BASE_URL/api/admin/cards/cover" \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -b "$COOKIE_JAR" \
  -F "image=@./new-default-card.jpg;type=image/jpeg"
```

### Create A Collectible Card

```bash
curl -X POST "$API_BASE_URL/api/admin/cards" \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -b "$COOKIE_JAR" \
  -F "name=Course au classement" \
  -F "categoryId=$CATEGORY_ID" \
  -F "enabled=true" \
  -F "displayOrder=10" \
  -F "image=@./card.png;type=image/png"
```

The API also accepts `category=<existing category name>`, but `categoryId` is preferred.

### Update A Collectible Card

Metadata and image can be updated together or separately.

```bash
curl -X PATCH "$API_BASE_URL/api/admin/cards/$CARD_ID" \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -b "$COOKIE_JAR" \
  -F "name=Course au classement" \
  -F "categoryId=$CATEGORY_ID" \
  -F "enabled=true" \
  -F "displayOrder=20" \
  -F "image=@./replacement.png;type=image/png"
```

If the image is omitted, only metadata changes. If metadata is omitted, only the image changes.

For image-only updates, prefer the dedicated route:

```bash
curl -X PATCH "$API_BASE_URL/api/admin/cards/$CARD_ID/image" \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -b "$COOKIE_JAR" \
  -F "image=@./replacement.png;type=image/png"
```

### Reorder Cards

Submit the full card order for a category. The API rewrites `displayOrder` from the array position, so inserting or moving a card is just a matter of sending the final list.

```bash
curl -X PUT "$API_BASE_URL/api/admin/cards/categories/$CATEGORY_ID/order" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -b "$COOKIE_JAR" \
  -d '{
    "cardIds": [
      "22222222-2222-2222-2222-222222222222",
      "33333333-3333-3333-3333-333333333333"
    ]
  }'
```

### Grant A Virtual Card

```bash
curl -X POST "$API_BASE_URL/api/admin/cards/$CARD_ID/collections?collectionType=VIRTUAL" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -b "$COOKIE_JAR" \
  -d '{
    "userId": "11111111-1111-1111-1111-111111111111",
    "quantity": 1
  }'
```

If `collectionType` is omitted, the API uses `VIRTUAL`.

### Assign A Physical Card Instance

Physical cards must be unique `CardInstance` rows. Assign an existing instance:

Create an instance from the card id, with an optional printed claim code and optional owner:

```bash
curl -X POST "$API_BASE_URL/api/admin/cards/instances" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -b "$COOKIE_JAR" \
  -d '{
    "cardId": "22222222-2222-2222-2222-222222222222",
    "publicId": "ABCDE-FGHJK-MNPQR",
    "ownerUserId": "11111111-1111-1111-1111-111111111111"
  }'
```

If `publicId` is omitted, the API generates a readable unique claim code. If `ownerUserId` is omitted, the instance is created unassigned.

```bash
curl -X POST "$API_BASE_URL/api/admin/cards/instances/$INSTANCE_ID/assignment" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -b "$COOKIE_JAR" \
  -d '{
    "userId": "11111111-1111-1111-1111-111111111111"
  }'
```

Release an assigned instance:

```bash
curl -X DELETE "$API_BASE_URL/api/admin/cards/instances/$INSTANCE_ID/assignment" \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -b "$COOKIE_JAR"
```

Search physical instances:

```bash
curl "$API_BASE_URL/api/admin/cards/instances?publicId=ABCDE-FGHJK-MNPQR" \
  -b "$COOKIE_JAR"
```

See [Card admin frontend](card-admin-frontend.md) for the full physical instance and batch UI flow.

### Read A User Deck

Read the virtual deck:

```bash
curl "$API_BASE_URL/api/cards/deck?collectionType=VIRTUAL" \
  -b "$COOKIE_JAR"
```

Read the physical deck:

```bash
curl "$API_BASE_URL/api/cards/deck?collectionType=PHYSICAL" \
  -b "$COOKIE_JAR"
```

If `collectionType` is omitted, the API uses `VIRTUAL`.

Example response shape:

```json
{
  "ownedCount": 1,
  "totalCount": 2,
  "categories": [
    {
      "category": "Legendary",
      "ownedCount": 1,
      "totalCount": 2,
      "cards": [
        {
          "id": "22222222-2222-2222-2222-222222222222",
          "name": "Course au classement",
          "categoryId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
          "category": "Legendary",
          "defaultCard": false,
          "quantity": 1,
          "displayOrder": 10,
          "imageUrl": "https://s3.example.com/insastronaute-public/cards/22222222-2222-2222-2222-222222222222/version/original.jpg",
          "thumbnailUrl": "https://s3.example.com/insastronaute-public/cards/22222222-2222-2222-2222-222222222222/version/thumbnail.jpg"
        },
        {
          "id": "33333333-3333-3333-3333-333333333333",
          "name": "???",
          "category": "???",
          "defaultCard": false,
          "quantity": 0,
          "displayOrder": 20,
          "imageUrl": "https://s3.example.com/insastronaute-public/cards/00000000-0000-0000-0000-000000000000/version/original.jpg",
          "thumbnailUrl": "https://s3.example.com/insastronaute-public/cards/00000000-0000-0000-0000-000000000000/version/thumbnail.jpg"
        }
      ]
    }
  ]
}
```

## File Upload And Download

Card images are uploaded through the API as `multipart/form-data`; clients do not upload card images directly to S3.

Upload constraints:

- The form field name must be `image`.
- JPEG and PNG are accepted.
- Maximum file size is 20 MB.
- The declared multipart content type must match the detected image type when provided.

Storage behavior:

- A compressed JPEG display image is stored in the public S3 bucket configured by `S3_PUBLIC_BUCKET`.
- A smaller JPEG thumbnail is generated automatically.
- Objects are stored under `cards/{cardId}/{version}/`.
- New image uploads create a new object version; the API deletes the previous display image and thumbnail after metadata is updated.
- Returned URLs are built from `S3_PUBLIC_ENDPOINT` and `S3_PUBLIC_BUCKET`.

Download behavior:

- Use `thumbnailUrl` for deck grids and compact views.
- Use `imageUrl` when the user opens a full card detail view.
- Do not call the backend to download card files; the URLs are public S3 object URLs.
- Cache returned URLs normally. The object paths include a generated version, so replacing an image returns different URLs.
