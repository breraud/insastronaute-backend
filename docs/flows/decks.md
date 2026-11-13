# Decks and Pulls

This document explains how to configure purchasable card decks and how the pull engine behaves.

## Admin: Create a Deck

Endpoint:

```http
POST /api/admin/decks
Content-Type: multipart/form-data
```

Multipart parts:

- `image`: deck cover image.
- `deckData`: JSON payload.

Example `deckData`:

```json
{
  "name": "Starter Boost",
  "price": 100,
  "cardsPerPull": 5,
  "dropTiers": [
    {
      "label": "Rare",
      "dropChancePercent": 3.0,
      "minCardsPerPull": 1,
      "maxCardsPerPull": 1,
      "cardIds": [
        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
      ]
    },
    {
      "label": "Uncommon",
      "dropChancePercent": 22.0,
      "maxCardsPerPull": 2,
      "cardIds": [
        "cccccccc-cccc-cccc-cccc-cccccccccccc"
      ]
    },
    {
      "label": "Common",
      "dropChancePercent": 75.0,
      "cardIds": [
        "dddddddd-dddd-dddd-dddd-dddddddddddd",
        "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"
      ]
    }
  ]
}
```

Fields:

- `name`: deck name.
- `price`: token cost paid once per pull.
- `cardsPerPull`: number of cards returned by one pull. Optional, defaults to `1`.
- `dropTiers[].label`: optional display label.
- `dropTiers[].dropChancePercent`: weighted chance for this tier. All tiers must sum to `100`.
- `dropTiers[].minCardsPerPull`: optional guaranteed quantity for this tier inside a single pull. `0` or omission means no guarantee.
- `dropTiers[].maxCardsPerPull`: optional quota for this tier inside a single pull.
- `dropTiers[].cardIds`: enabled collectible cards available in this tier.

Admin deck creation can reference cards from hidden or game categories. They remain present in admin management responses, but player-facing deck detail and pull routes ignore them.

## Admin: Update a Deck

```http
PATCH /api/admin/decks/{deckId}
Content-Type: multipart/form-data
```

Send the same `deckData` part as for creation. The `image` part is optional; omitting it keeps the existing deck cover. Updating a deck replaces all of its drop tiers with those in `deckData`.

## Player: Browse Decks

List decks:

```http
GET /api/decks
```

Get a deck with its loot table:

```http
GET /api/decks/{deckId}
```

The detail response includes `cardsPerPull`, each visible tier's configured percentage, each visible tier's guaranteed quantity and quota, and the public cards in the tier. Cards from hidden and game categories are omitted. If all cards in a tier are hidden or game-only, the tier is omitted from the public detail response.

## Player: Pull Cards

Endpoint:

```http
POST /api/decks/{deckId}/pull
```

The server:

1. Locks the player row for the transaction.
2. Checks the token balance.
3. Deducts the deck price.
4. Draws `cardsPerPull` cards.
5. Ignores cards from hidden and game categories and skips tiers that have no public cards.
6. Draws each remaining public tier's `minCardsPerPull` guaranteed cards first.
7. For each remaining card slot, selects one eligible tier with weighted random.
8. Excludes a tier from the remaining slots when its `maxCardsPerPull` quota is reached.
9. Draws one random public card uniformly inside the selected tier.
10. Adds each card to the player's virtual collection.

Example: with `cardsPerPull = 5` and a `Rare` tier at `3%` with `minCardsPerPull = 1` and `maxCardsPerPull = 1`, the player receives exactly one rare card from that pull. With only `maxCardsPerPull = 1`, the player can receive at most one rare card, but it is not guaranteed.

Response:

```json
{
  "deckId": "11111111-1111-1111-1111-111111111111",
  "remainingTokens": 900,
  "drops": [
    {
      "dropTierId": "22222222-2222-2222-2222-222222222222",
      "dropTierLabel": "Rare",
      "card": {
        "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        "name": "Rare Card"
      }
    }
  ]
}
```

The transaction rolls back completely if any error occurs, so the player does not lose tokens on a failed pull.

## Admin: Delete a Deck

`DELETE /api/admin/decks/{deckId}` removes a deck and its tiers, and clears its visuals from storage.

Nothing else in the database points at a deck: a pulled card joins the collection without recording where it came from, and tier templates live beside decks rather than inside them. Deleting a deck therefore takes nothing away from any player — only the offer in the shop disappears.

Storage cleanup is best-effort. Each saved image writes under a fresh prefix, so an often-reillustrated deck leaves several; they are swept by deck id. If that sweep fails the deck is still deleted and an orphan object remains, which is the better failure: the opposite would leave a deck nobody can remove because its image refuses to go.

## Admin: Save and Reuse Tier Templates

Rebuilding the same tier structure card by card for every new pack is the slowest part of opening a shop. A template stores one arrangement under a name so another pack can start from it.

A template is only ever a starting point. Loading one fills the pack form and nothing else — nothing is written until the pack itself is saved, so the arrangement stays free to adjust. Templates never point at a pack and packs never remember the template they came from: editing one has no effect on the other.

`GET /api/admin/decks/tier-templates` lists them, newest naming order first:

```json
[
  {
    "id": "33333333-3333-3333-3333-333333333333",
    "name": "Standard",
    "cardsPerPull": 5,
    "dropTiers": [
      { "id": "…", "label": "Commune", "dropChancePercent": 97.0, "cards": [{ "id": "…", "name": "…" }] },
      { "id": "…", "label": "Rare", "dropChancePercent": 3.0, "minCardsPerPull": 1, "maxCardsPerPull": 1, "cards": [] }
    ],
    "updatedAt": "2026-08-27T14:00:00Z"
  }
]
```

`POST` saves one, `PUT /{templateId}` replaces it, `DELETE /{templateId}` removes it. The body mirrors a deck's tiers:

```json
{
  "name": "Standard",
  "cardsPerPull": 5,
  "dropTiers": [
    { "label": "Commune", "dropChancePercent": 97, "cardIds": ["…"] },
    { "label": "Rare", "dropChancePercent": 3, "minCardsPerPull": 1, "maxCardsPerPull": 1, "cardIds": ["…"] }
  ]
}
```

A template answers to the same arithmetic as a pack — chances summing to 100, guaranteed minimums fitting inside one pull, quotas leaving room to fill it — because a template that could not be poured into a pack would only be discovered once the form was already filled. That is why it carries its own `cardsPerPull`: the rules mean nothing without one.

Templates are read back one tier at a time, never once per card: fetching the tiers and their cards in a single entity graph joins two collections and multiplies the rows, and a `List` does not deduplicate. The cards load on demand inside the same transaction instead.

Names are unique, case-insensitively: two templates sharing a name would be indistinguishable in the only place they are ever chosen from. A card deleted from the catalogue simply leaves the templates that named it, unlike a live pack, which refuses the deletion.


## Tier Rarity, Measured

A drop chance means nothing on its own. Ten percent in a pack that draws ten cards shows up in almost every opening; the same ten percent in a pack that draws one is a ten-pack wait. Comparing packs — and choosing how loudly to celebrate a card — needs a number that already accounts for that.

Every tier therefore carries three computed fields, in `GET /api/decks/{deckId}` and on each drop of a pull:

- `pullProbabilityPercent` — the chance of seeing this tier at least once in one opening
- `expectedPullsForOne` — how many openings, on average, before one shows up
- `rarity` — `COMMON`, `UNCOMMON`, `RARE`, `EPIC` or `LEGENDARY`, banded on the number of openings rather than on the percentage: under 2 openings is common, under 5 uncommon, under 15 rare, under 50 epic, beyond that legendary

A tier with a guaranteed minimum drops every time, so its probability is 1 and it is never dramatised. The other tiers share the slots left once those minimums are served, which is why a pack full of guarantees makes its open tiers rarer than their percentages suggest.

The model ignores the without-replacement draw and the per-tier quotas on purpose: it exists to choose an animation, not to publish a payout table. One definition lives in `DropTierRules` so the shop, the reveal and the admin form cannot drift apart.
