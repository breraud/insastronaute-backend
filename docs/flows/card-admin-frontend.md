# Card Admin Frontend Implementation Flow

This document describes how the frontend should implement admin card instance management, physical print batches, and printed page lookup.

## Scope

Build these admin views:

- Card catalog management: existing card definitions, images, categories, ordering, and virtual grants.
- Physical card instance management: searchable unique physical cards, owner assignment, reassignment, release, update, and delete.
- Print batch history: generated PDFs, generated cards, page list, and page stamp lookup.
- Print page lookup: enter the id printed in the lower-left corner of a physical page and jump to the exact batch/page/cards.

All endpoints are under `/api/admin/**`, so the logged-in user must have `ADMIN`.
All state-changing browser requests must include `X-XSRF-TOKEN` from `GET /api/csrf`.

## Core Model

There are two different card concepts:

- `Card`: the catalog definition. It has the card name, category/type, display order/number, image URLs, and enabled state.
- `CardInstance`: one physical printed card. It has one `publicId` claim code and at most one current owner.

Virtual ownership is still quantity-based through `CardCollection`.
Physical ownership must use a unique `CardInstance`; do not create physical ownership with collection grants.

## Recommended Admin Navigation

Use this structure:

- `Cards`
  - catalog list/create/edit
  - card categories/order
  - virtual grants
- `Physical Cards`
  - instance search table
  - instance detail/edit drawer
- `Print Batches`
  - batch list
  - batch detail
  - printed page lookup
- `Print Templates`
  - template upload
  - calibration
  - preview
  - generate batch

## Physical Card Instance Search

Use:

```http
GET /api/admin/cards/instances?publicId={code}&ownerUserId={userId}&cardId={cardId}&type={type}&number={number}
```

All query parameters are optional.

Search fields:

- `publicId`: printed claim code on one card. The backend accepts lower case, spaces, or missing dashes and normalizes it.
- `ownerUserId`: current owner user id.
- `cardId`: exact catalog card id.
- `type`: searches card category/name.
- `number`: card display order.

Example response:

```json
[
  {
    "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "cardId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
    "cardName": "Carte Rare",
    "type": "Rare",
    "number": 7,
    "publicId": "ABCDE-FGHJK-MNPQR",
    "ownerUserId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
    "ownerDisplayName": "Player",
    "ownerEmail": "player@example.com",
    "printRunId": "dddddddd-dddd-dddd-dddd-dddddddddddd",
    "printSheetIndex": 0,
    "frontPrintPageId": "dddddddd-dddd-dddd-dddd-dddddddddddd-1",
    "backPrintPageId": "dddddddd-dddd-dddd-dddd-dddddddddddd-2",
    "assignedAt": "2026-01-01T00:00:00Z",
    "createdAt": "2026-01-01T00:00:00Z",
    "updatedAt": "2026-01-01T00:00:00Z"
  }
]
```

Frontend table columns:

- public code
- card name
- type
- number
- owner display/email
- batch id
- page ids
- assigned date
- actions

## Physical Card Instance Detail

Use:

```http
GET /api/admin/cards/instances/{instanceId}
```

Show the same fields as the search response.
Provide quick links:

- open batch detail with `printRunId`
- open front page lookup with `frontPrintPageId`
- open back page lookup with `backPrintPageId`

## Create A Physical Card Instance

Use:

```http
POST /api/admin/cards/instances
Content-Type: application/json
X-XSRF-TOKEN: <token>
```

```json
{
  "cardId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
  "publicId": "ABCDE-FGHJK-MNPQR",
  "ownerUserId": "cccccccc-cccc-cccc-cccc-cccccccccccc"
}
```

Only `cardId` is required. If `publicId` is omitted, the backend generates one. If `ownerUserId` is omitted, the physical instance is created unassigned.

The response is the same `AdminCardInstanceDto` shape used by search/detail, so the frontend can prepend or replace the row directly.

## Update A Physical Card Instance

Use `PATCH` for partial edits:

```http
PATCH /api/admin/cards/instances/{instanceId}
Content-Type: application/json
X-XSRF-TOKEN: <token>
```

Change the card definition:

```json
{
  "cardId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
}
```

Change the public code:

```json
{
  "publicId": "ABCDE-FGHJK-MNPQR"
}
```

Assign or reassign the owner:

```json
{
  "ownerUserId": "cccccccc-cccc-cccc-cccc-cccccccccccc"
}
```

Release the owner:

```json
{
  "clearOwner": true
}
```

You can combine fields in one request:

```json
{
  "cardId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
  "publicId": "ABCDE-FGHJK-MNPQR",
  "ownerUserId": "cccccccc-cccc-cccc-cccc-cccccccccccc"
}
```

Frontend behavior:

- After save, replace the row/detail with the returned `AdminCardInstanceDto`.
- If `clearOwner` is true, omit `ownerUserId`.
- If changing `cardId` while the instance is owned, the backend moves the physical collection count from the old card to the new card.
- If changing `ownerUserId`, the backend releases the old owner and assigns the new owner atomically.

## Existing Assignment Shortcuts

These endpoints still exist and are useful for simple actions:

Assign:

```http
POST /api/admin/cards/instances/{instanceId}/assignment
```

```json
{
  "userId": "cccccccc-cccc-cccc-cccc-cccccccccccc"
}
```

Release:

```http
DELETE /api/admin/cards/instances/{instanceId}/assignment
```

Delete:

```http
DELETE /api/admin/cards/instances/{instanceId}
```

Deletion permanently removes the physical instance and adjusts the owner's physical collection count if assigned.

## Virtual Grants

Virtual cards remain quantity-based.

Use:

```http
POST /api/admin/cards/{cardId}/collections?collectionType=VIRTUAL
```

```json
{
  "userId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
  "quantity": 1
}
```

Do not send `collectionType=PHYSICAL` for this endpoint.
The API rejects it because physical cards must be unique instances.

## Print Batch Creation

Batch generation still starts from the print template flow:

```http
POST /api/admin/card-print-runs
Content-Type: application/json
X-XSRF-TOKEN: <token>
```

```json
{
  "templateId": "11111111-1111-1111-1111-111111111111",
  "sheetCount": 10,
  "slots": [
    { "slotIndex": 0, "cardId": "22222222-2222-2222-2222-222222222222" },
    { "slotIndex": 1, "cardId": "33333333-3333-3333-3333-333333333333" },
    { "slotIndex": 2, "cardId": "44444444-4444-4444-4444-444444444444" },
    { "slotIndex": 3, "cardId": "55555555-5555-5555-5555-555555555555" },
    { "slotIndex": 4, "cardId": "66666666-6666-6666-6666-666666666666" },
    { "slotIndex": 5, "cardId": "77777777-7777-7777-7777-777777777777" },
    { "slotIndex": 6, "cardId": "88888888-8888-8888-8888-888888888888" },
    { "slotIndex": 7, "cardId": "99999999-9999-9999-9999-999999999999" },
    { "slotIndex": 8, "cardId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa" }
  ]
}
```

Rules:

- `slots` must contain indexes `0` through `8` exactly once.
- `sheetCount` is limited to 100.
- The backend creates `sheetCount * 9` physical card instances.
- The backend generates two PDF pages per sheet.
- The generated PDF URL is returned as `pdfUrl`.

Each generated page receives a lower-left stamp:

```text
<batch UUID>-<page number>
```

Example:

```text
dddddddd-dddd-dddd-dddd-dddddddddddd-4
```

Page numbering starts at `1`.
Odd pages are `FRONT`, even pages are `BACK`.

## Batch List

Use:

```http
GET /api/admin/batches
```

Show a table with:

- batch id
- template name
- sheet count
- page count
- generated card count
- status
- created date
- PDF action
- details action

Response item shape:

```json
{
  "id": "dddddddd-dddd-dddd-dddd-dddddddddddd",
  "templateId": "11111111-1111-1111-1111-111111111111",
  "templateName": "A4 9 cartes",
  "sheetCount": 10,
  "pageCount": 20,
  "instanceCount": 90,
  "status": "COMPLETED",
  "pdfUrl": "https://s3.example.com/card-print/runs/file.pdf",
  "pages": [],
  "slots": [],
  "createdAt": "2026-01-01T00:00:00Z",
  "completedAt": "2026-01-01T00:00:01Z"
}
```

The list response includes pages and slots. If the UI only needs the table, render the summary fields and load the detail view on click.

## Batch Detail

Use:

```http
GET /api/admin/batches/{batchId}
```

Recommended layout:

- Header: batch id, template, status, dates, PDF link.
- Pages tab: one row per PDF page.
- Cards tab: one row per generated card instance.

Page object:

```json
{
  "printPageId": "dddddddd-dddd-dddd-dddd-dddddddddddd-4",
  "pageNumber": 4,
  "sheetIndex": 1,
  "side": "BACK",
  "slots": [
    {
      "pageIndex": 1,
      "slotIndex": 0,
      "cardId": "22222222-2222-2222-2222-222222222222",
      "cardName": "Carte Rare",
      "cardInstanceId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
      "publicId": "ABCDE-FGHJK-MNPQR"
    }
  ]
}
```

Slot/card object:

```json
{
  "pageIndex": 1,
  "slotIndex": 0,
  "cardId": "22222222-2222-2222-2222-222222222222",
  "cardName": "Carte Rare",
  "cardInstanceId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "publicId": "ABCDE-FGHJK-MNPQR"
}
```

For a back page, `slots` still lists the cards on that physical sheet.
Use `sheetIndex` or `pageIndex` to group front/back pages with the same 9 cards.

## Batch Cards Endpoint

Use this when the UI only needs the generated cards:

```http
GET /api/admin/batches/{batchId}/cards
```

Render:

- sheet index (`pageIndex`)
- slot index
- card name
- instance id
- public id
- action to open physical card instance detail

## Printed Page Lookup

Add a search input in the admin interface:

```text
Printed page id
```

User enters the exact lower-left stamp from the physical page, for example:

```text
dddddddd-dddd-dddd-dddd-dddddddddddd-4
```

Call:

```http
GET /api/admin/batches/pages/{printPageId}
```

Response:

```json
{
  "batchId": "dddddddd-dddd-dddd-dddd-dddddddddddd",
  "printPageId": "dddddddd-dddd-dddd-dddd-dddddddddddd-4",
  "pageNumber": 4,
  "sheetIndex": 1,
  "side": "BACK",
  "pdfUrl": "https://s3.example.com/card-print/runs/file.pdf",
  "page": {
    "printPageId": "dddddddd-dddd-dddd-dddd-dddddddddddd-4",
    "pageNumber": 4,
    "sheetIndex": 1,
    "side": "BACK",
    "slots": []
  }
}
```

After lookup:

- Navigate to the batch detail page.
- Highlight the returned page.
- Show the cards in `page.slots`.
- Provide a PDF link using `pdfUrl`.

## Print Template Preview

Use preview before generating a real batch:

```http
POST /api/admin/card-print-templates/{templateId}/preview
```

The response is `application/pdf`.
It does not create physical card instances.
Preview pages are stamped with:

```text
TEST-1
TEST-2
```

Use this to verify that the stamp appears in the lower-left corner before generating a real print batch.

## Error Handling

Common frontend handling:

- `400`: invalid request, invalid public id, missing slots, invalid page id, physical grant attempted through collection endpoint.
- `404`: card, user, physical instance, batch, or page not found.
- `409`: duplicate public id, card already assigned during player claim, or unique constraint conflict.
- `403`: user is not an admin.

Display backend `message` from the API error response when available.

## Frontend State Rules

- Treat `CardInstance.ownerUserId === null` as unassigned.
- Treat `printRunId === null` as manually created or not generated from a print batch.
- Do not infer ownership from physical deck quantity when editing one instance; use the instance endpoints.
- Refresh card instance search results after assignment, release, update, or delete.
- Refresh batch details after generating a new print run.
- Keep the printed page id as a copyable value in batch page rows.

## Suggested Implementation Order

1. Add a reusable admin API client that attaches CSRF on state-changing requests.
2. Add physical card instance search and detail views.
3. Add assignment, release, update, and delete actions for physical instances.
4. Update virtual grant UI to only use `collectionType=VIRTUAL`.
5. Add batch list and batch detail views.
6. Add printed page lookup and page highlight behavior.
7. Add preview PDF display/download with visible `TEST-1` and `TEST-2` stamps.
8. Add a post-generation redirect from print run creation to `/admin/batches/{batchId}`.
