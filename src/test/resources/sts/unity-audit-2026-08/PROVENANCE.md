# STS combat data fixtures — `unity-audit-2026-08`

## What this is

A minimized copy of real Unity combat data, kept next to the backend so
`GameDataCompatibilityTest` runs without a Unity checkout and without any
machine-local path.

* `cards.json` — 31 entries copied verbatim from `Assets/StreamingAssets/STSCardData/`
  (per-card files, with `cards.json` as fallback), wrapped in the `{dataVersion, cards}`
  envelope the backend serves.
* `enemies.json` — 4 entries copied verbatim from `Assets/StreamingAssets/EnemyData/`.

Copied on 2026-08-21 from the Unity project at branch `main`. Nothing was edited:
if a value looks wrong, it is wrong in Unity too, and that is the point.

## Why these entries

The card set includes the 19-card CardSelection inventory used by the Unity integration, plus one representative of every family that still blocks the parser
(`combustion` for `xCost`, `construction` for `modifiers`, `capteur_de_mouvement` for
`conditional`, `galanterie` for `trueEffect`), one card with no effects at all
(`archi`), one Power (`divination`), one tagged card (`acharnement`), and every card
those entries create through a `cardID` reference, so the fixture resolves on its own.

The enemies cover the four shapes the engine has to handle: inline moves mixing a
self-targeted and an opponent-targeted effect (`Aegis_Drone`), inline moves carrying a
CardSelection (`Devourer`), a pattern given as `patternCardIds` (`Cultist`), and moves
that reference catalog cards by `cardId` (`Coeurl`).

## Updating

When a new `dataVersion` ships, add a sibling directory rather than editing this one:
a `dataVersion` is immutable by contract.
