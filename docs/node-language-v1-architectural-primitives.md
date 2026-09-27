# Architectural Primitives — Node Language v1

**Status: PASSED / FROZEN** (Graph **V68**)

Architectural Primitives v1 freezes the 18 `geometry.architectural_primitives` nodes as a language-stable building-block family: strict inputs, Valid/Error, instance budgets, and composable continuous geometry outputs.

**Out of scope:** `GEOMETRY_LIST`, new arch nodes, new roof types, stair algorithm rewrite, BIM/materials, full eave/ridge `PATH_LIST` topology.

## Inventory (order 0–17)

| Order | Display | Id | Effect |
|------:|---------|-----|--------|
| 0 | Window Array | `geometry.architectural_primitives.window_array` | `PURE` |
| 1 | Door Array | `geometry.architectural_primitives.door_array` | `PURE` |
| 2 | Column Grid | `geometry.architectural_primitives.column_grid` | `PURE` |
| 3 | Railing | `geometry.architectural_primitives.railing` | `PURE` |
| 4 | Roof Base | `geometry.architectural_primitives.roof_base` | `PURE` |
| 5 | Staircase | `geometry.architectural_primitives.staircase` | `PURE` |
| 6 | Roof Generator | `geometry.architectural_primitives.roof_generator` | `PURE` |
| 7 | Facade Panel Array | `geometry.architectural_primitives.facade_panel_array` | `PURE` |
| 8 | Arch Opening | `geometry.architectural_primitives.arch_opening` | `PURE` |
| 9 | Wall With Openings | `geometry.architectural_primitives.wall_with_openings` | `PURE` |
| 10 | Pilaster / Cornice | `geometry.architectural_primitives.pilaster_cornice` | `PURE` |
| 11 | Array Along Curve | `geometry.architectural_primitives.array_along_curve` | `PURE` |
| 12 | Floor Slab | `geometry.architectural_primitives.floor_slab` | `PURE` |
| 13 | Beam Grid | `geometry.architectural_primitives.beam_grid` | `PURE` |
| 14 | Molding Profile | `geometry.architectural_primitives.molding_profile` | `PURE` |
| 15 | Wall Along Path | `geometry.architectural_primitives.wall_along_path` | `PURE` |
| 16 | Beam Along Path | `geometry.architectural_primitives.beam_along_path` | `PURE` |
| 17 | Column | `geometry.architectural_primitives.column` | `PURE` |

**Removed (V68):** `floor_slab_with_beams`, `deconstruct_opening`.

## Shared input contract

[`ArchitecturalInputUtils`](../src/main/java/com/nodecraft/nodesystem/util/ArchitecturalInputUtils.java) (via `OptionalPortDrive` / `StrictIntegerUtils`):

- unconnected → property/default
- connected valid → override
- connected invalid/null → fail closed

No graph-facing `Number.intValue()` repair. No permissive `resolvePositiveInt/Double`.

## Budgets ([`GenerationLimits`](../src/main/java/com/nodecraft/nodesystem/util/GenerationLimits.java))

- `MAX_ARCHITECTURAL_INSTANCES` (= `MAX_GEOMETRY_INSTANCES`)
- `MAX_ARCHITECTURAL_PATH_SEGMENTS`
- `MAX_ARCHITECTURAL_PROFILE_SEGMENTS`

Face arrays / grids: long-first `columns × rows` before allocation. Over budget → `Valid=false`, transactional zero partial output.

## Geometry packing

[`GeometryOutputUtils.packGeometry`](../src/main/java/com/nodecraft/nodesystem/util/GeometryOutputUtils.java): `0→null`, `1→raw`, `2+→Composite`. Null members → `null` (fail closed; no silent filter). Face-array factories that return null abort the whole array (`Valid=false`).

## High-signal node rules

- **Column:** Frame XOR Base (`PointData` only); Shape ∈ `{cylinder, box, frustum}`
- **Roof Base:** `{flat, shed, gable}` only; Primary Eave Path
- **Roof Generator:** specialty `{asymmetric_gable, hip, cross_gable, m}` only; Primary Ridge / Primary Eave Path
- **Staircase:** layouts `{straight, u, double_run, switchback, spiral}`; exact Step Count / First Flight; no clamp; `straight` follows PATH; complex layouts use chord orientation
- **Wall With Openings:** Wall + Openings separate (no auto Difference); Opening Depth unconnected → wallThickness
- **Face arrays:** Count = total emitted instances

## Migration (V67 → V68)

Remove nodes of types `floor_slab_with_beams` / `deconstruct_opening` and incident wires (incl. subgraphs).

## Verification

```text
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.ArchitecturalPrimitivesLanguageContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.FaceArrayTransactionalContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.ArchitecturalFamilyContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.GraphFormatVersionContractTest"
```
