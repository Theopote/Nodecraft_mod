# Node Language v2 — Placement Semantics & Budgeting

**Status: PASSED / FROZEN** (Graph **V100**)

Remediation over [node-language-v1-placement.md](node-language-v1-placement.md) (V54 inventory and block-grid snap remain frozen). V76 leaf×frame workload contracts remain in force.

## Apply Frame Orientation (was Orient Geometry To Frame)

typeId unchanged: `transform.placement.orient_geometry_to_frame`.

**Semantics:** apply FRAME rotation **relative** to existing geometry coordinates about a fixed world pivot (`GeometryTransform.transformAround`). This is **not** an absolute set-to-frame.

| Place Geometry On Frames | Apply Frame Orientation |
|--------------------------|-------------------------|
| rotate + move pivot → frame origin | rotate only; pivot stays fixed |
| local tilt under Frame is intentional | relative multiply onto existing orientation |

Absolute “orientation = Frame” is undefined for general `GeometryData` (Sphere / Prism / Composite / SDF) and is out of scope.

## Budgeting (V100)

| Helper | Behavior |
|--------|----------|
| `FrameUtils.resolveStrictFrameListBounded(value, max)` | size check **before** allocating the FRAME_LIST copy |
| `GeometryStructureUtils.countLeavesBounded(geometry, limit)` | walk Composite leaves without materializing a leaf List; returns `limit + 1` and stops early when over budget |

`Place Geometry On Frames` uses both for FRAME_LIST resolve and `sourceLeaves × frameCount` preflight.

## Migration

V99 → V100 is a no-op (display/runtime budgeting only; no wire/port changes).

## Verification

```text
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.PlacementLanguageContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.PlacementLanguageV2ContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.TransformFamilyContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.GraphFormatVersionContractTest"
```
