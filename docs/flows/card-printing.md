# Physical Card Printing Flow

This document describes the admin flow for generating printable physical card sheets.

## System Summary

The printing system generates duplex PDF sheets containing physical card instances.
Admins provide a two-page PDF print template, calibrate the 9 card positions on page 1, select the cards that should appear on each sheet, choose a number of sheets, and generate a final PDF ready to print.

Every template follows the same page convention:

- Page 1 is the front. It is calibrated and receives the selected card faces plus claim codes.
- Page 2 is the back. It is reused as-is after each generated front page.
- Every generated output page is stamped in the lower-left corner with a page id.

Each printed card receives a unique alphanumeric claim code from the existing `CardInstance.publicId` system. Generated instances remain unassigned until a player claims them with `POST /api/cards/instances/claim`.
Each generated PDF page receives a public print page id in this format:

```text
<batch UUID>-<page number>
```

The page id can be searched from the admin frontend with `GET /api/admin/batches/pages/{printPageId}` to recover the batch, PDF page, sheet, side, and cards on that page.

The flow is split in two modes:

- Preview: renders a test PDF without creating physical instances.
- Print run: creates the real `CardInstance` rows, renders their public codes, stores the generated PDF privately, and returns the authenticated backend PDF URL.

## Concepts

- A print template is a two-page PDF patron stored in the public S3 bucket under `card-print/templates/`.
- A template calibration contains exactly 9 card slots, a card image fit mode, optional bleed, and code text settings.
- Coordinates are stored in PDF points (`1 pt = 1/72 inch`) with a top-left origin on page 1.
- A preview renders a PDF without creating card instances.
- A print run creates physical `CardInstance` rows, renders their `publicId` on the selected cards, stores the generated duplex PDF under `card-print/runs/` as a private S3 object, stamps each PDF page, and returns the backend URL.
- A print batch is the persisted print run history. It exposes generated cards, pages, and the authenticated backend PDF URL.

## Template Calibration

The template PDF contains the printed sheet layout. The backend requires at least two pages, reads the first page dimensions automatically, checks that pages 1 and 2 have the same size, then the admin calibration stores the 9 card slots for page 1.

Slot coordinates use PDF points:

```text
1 pt = 1/72 inch
```

Coordinates use a top-left origin on page 1, matching tools like Inkscape. The backend converts them to PDFBox's bottom-left coordinate system during rendering.

## Code Rendering

The printed claim code is configured per template and positioned relative to each card slot.

The template stores:

- code box position and size;
- font family;
- font weight;
- font size;
- text color;
- optional background color;
- background opacity;
- text alignment.

## Admin Flow

1. Upload the PDF patron with `POST /api/admin/card-print-templates`.
2. Read `pageWidthPt` and `pageHeightPt` from the response.
3. Calibrate the 9 slots and code settings with `PUT /api/admin/card-print-templates/{id}/calibration`.
4. Generate a preview with `POST /api/admin/card-print-templates/{id}/preview`.
5. Adjust the frontend calibration until front-page card placement, crop mode, and code rendering are validated.
6. Enable the template in the calibration request.
7. Generate a real print run with `POST /api/admin/card-print-runs`.
8. Redirect the admin to `GET /api/admin/batches/{id}` to inspect generated pages and cards, then fetch the PDF through `GET /api/admin/batches/{id}/pdf`.

Unused templates can be deleted. Templates already used by a real print run cannot be deleted because print runs and generated card instances keep that template as audit context.

## Calibration Request

```json
{
  "enabled": true,
  "fitMode": "COVER",
  "bleedPt": 2,
  "slots": [
    {
      "slotIndex": 0,
      "xPt": 36.85,
      "yPt": 29.76,
      "widthPt": 178.55,
      "heightPt": 249.39
    }
  ],
  "code": {
    "xPt": 10,
    "yPt": 220,
    "widthPt": 150,
    "heightPt": 16,
    "fontFamily": "HELVETICA",
    "fontWeight": "BOLD",
    "fontSizePt": 9,
    "color": "#111111",
    "backgroundColor": "#FFFFFF",
    "backgroundOpacity": 0.75,
    "align": "CENTER"
  }
}
```

The `slots` array must contain indexes `0` through `8` exactly once. The code box is relative to each card slot, not to the page.
Use `bleedPt` when thin white gaps or cut gutters remain visible between printed cards. It expands the rendered card image around each calibrated slot without changing the stored slot coordinates.

## Preview Request

```json
{
  "slots": [
    { "slotIndex": 0, "cardId": "22222222-2222-2222-2222-222222222222" },
    { "slotIndex": 1, "cardId": "33333333-3333-3333-3333-333333333333" }
  ]
}
```

The real request must contain 9 selected cards. The response is `application/pdf`.
The preview PDF is stamped with test page ids `TEST-1` and `TEST-2`.

## Print Run Request

```json
{
  "templateId": "11111111-1111-1111-1111-111111111111",
  "sheetCount": 10,
  "slots": [
    { "slotIndex": 0, "cardId": "22222222-2222-2222-2222-222222222222" },
    { "slotIndex": 1, "cardId": "33333333-3333-3333-3333-333333333333" }
  ]
}
```

The real request must contain 9 selected cards. `sheetCount` is limited to 100, so one request can create at most 900 physical instances and a 200-page duplex PDF.

The response includes the generated print run id. Use that id as the batch id for batch history routes.
The `pdfUrl` in print-run and batch responses points to the authenticated backend route, not to S3.
Generated PDFs contain claim codes. Keep `card-print/runs/` out of anonymous S3 read policies, and keep existing run PDF object ACLs private.

## Batch History

List generated batches:

```http
GET /api/admin/batches
```

Get one batch:

```http
GET /api/admin/batches/{batchId}
```

Download the generated batch PDF:

```http
GET /api/admin/batches/{batchId}/pdf
```

List the cards generated by one batch:

```http
GET /api/admin/batches/{batchId}/cards
```

Resolve a printed page stamp:

```http
GET /api/admin/batches/pages/{printPageId}
```

Pages are numbered from `1`. Odd pages are `FRONT`, even pages are `BACK`. `sheetIndex` is zero-based and groups the front/back page for the same physical sheet.

## Delete Templates

Delete one unused template:

```http
DELETE /api/admin/card-print-templates/{id}
```

Delete multiple unused templates:

```http
DELETE /api/admin/card-print-templates
```

```json
{
  "templateIds": [
    "11111111-1111-1111-1111-111111111111",
    "22222222-2222-2222-2222-222222222222"
  ]
}
```

Batch deletion is all-or-nothing at validation time: if one requested template is missing, duplicated, or already used by a print run, the request is rejected.

## Frontend Notes

- Use a preview PDF before creating a real print run.
- Do not call the print run endpoint for calibration; it creates claimable physical instances.
- Inkscape values shown in points can be sent directly if the frontend uses the same top-left origin.
- If the UI reads Inkscape values in `px`, convert to points with `pt = px * 0.75`.
- Use `COVER` when cards must fill the whole slot, and `CONTAIN` when cropping is not acceptable.
- Start with `bleedPt: 0`, then test small values such as `1` or `2` points if the patron remains visible between adjacent cards.
