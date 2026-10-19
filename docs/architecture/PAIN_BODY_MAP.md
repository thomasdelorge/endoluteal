# Pain body map architecture

Agents: read this file with [AGENTS.md](../../AGENTS.md) before changing pain localization, `BodyMap`, `PainMark`, or recap pain payloads.

## Goal

Users place **pins** on the CHOIR female silhouette (front/back). Each pin has intensity and optional pain types. Several pins may share the same CHOIR zone key. Coordinates are stored for a future **heatmap over time** (aggregate pins across journal events and daily recaps).

## `PainMark` model

Defined in `fr.luteal.core.model.PainMark`:

| Field | Role |
|-------|------|
| `zone` | Stable CHOIR region key (e.g. `f08`) or internal `BodyZone` id |
| `intensity` | 0–10 |
| `painTypes` | List of `PainType` ids |
| `pinSide` | `"front"` or `"back"` when pinned on the silhouette |
| `pinX`, `pinY` | Normalized 0–1 within that half of the canvas |

`isPin` is true when `pinSide`, `pinX`, and `pinY` are all set.

**Internal zones** (vagin profond, rectum, etc.) are entered via chips below the map; they have no pin coordinates by design.

## `BodyMapInteraction`

| Mode | Use |
|------|-----|
| **`Pins`** | All **editing**: symptom sheet, medication intake (before/after), **daily recap pain step**. Tap silhouette → new pin; tap pin → edit. |
| **`Zones`** | **Read-only aggregation** only (e.g. `BodyMap(heat: Map<BodyZone, Int>)`, future min/max derived from data). **Do not** use for recap or journal entry. |

Default for `BodyMap(marks, onMarksChange)` is `Pins`.

## Persistence

| Source | Storage |
|--------|---------|
| Journal events (symptom, intake, pain check, …) | Room `pain_marks` linked to `journal_events` (`pinSide`, `pinX`, `pinY` since schema 11) |
| Daily recap pain step | `daily_recaps.painJson` → `RecapPainPayload.marks` (`RecapPainMark`, same pin fields) |
| Backup JSON | `pain_marks` on export; recap `pain` blob includes `marks` |

## CHOIR map

- Polygons: `assets/choir_painmap_female.txt`, parsed by `ChoirPainMap`.
- Hit-test on tap; pin `zone` = region `key`.
- Attribution string in UI (`choir_bodymap_attribution`).

## Legacy `zoneRanges` (recap)

Older recaps stored `RecapZoneRange` (min/max per zone). On load, `RecapPainPayload.withPinsOnly()` migrates to pins at the region polygon centroid, intensity = `maxIntensity`. Min/max per zone is **not** preserved (`ponytail:` recomputable later in analytics if needed).

## Do not

- Reintroduce whole-zone tap editing on journal or recap.
- Present aggregated or estimated pain as user-recorded pins.
- Add `INTERNET` for map assets (bundled locally).

## Related code

- UI: `fr.luteal.core.designsystem.component.bodymap.*`
- Recap: `PainStep`, `RecapPainPayload`, `RecapPainMigration`
- Tests: `PainPinGeometryTest`, `RecapPainMigrationTest`
