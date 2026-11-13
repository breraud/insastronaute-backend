# Relic catalog ids — `relic-catalog-2026-08`

## What this is

The 62 relic ids of `STS/GameData/relics.json`, copied on 2026-08-21, so
`RelicHandlerCatalogAlignmentTest` can run anywhere.

`/STS/` is git-ignored: the catalog is a developer's working copy of data the server
really reads from object storage, keyed by `dataVersion`. A test that reads it from disk
passes on a machine that happens to hold the file and fails on a fresh checkout — which
is exactly what it did in CI.

## Keeping it honest

The fixture is what CI checks against, so it can drift from the real catalog. The test
guards that drift itself: when `STS/GameData/relics.json` *is* present, it asserts that
this file still lists exactly the same ids. A developer who adds a relic sees the fixture
go stale on the first run; CI keeps a deterministic list either way.

Only the ids are copied. Nothing here decides how a relic behaves — the ids are the
lookup key `RelicEngine` uses, and matching them is the whole point of the test.

## The real fix, eventually

The authority on relic ids is the deployed data version, not a file in a checkout. When
the compatibility matrix grows to scan a full `dataVersion`, relic ids belong in that
scan and this fixture can go.
