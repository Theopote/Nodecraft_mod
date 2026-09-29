# Node Language v2 — Orientation / Frame Handedness & Projected Path Validity

**Status: PASSED / FROZEN** (Graph **V99**)

Remediation over Orientation Language v1 (Graph **V77**). Six-node inventory, Valid/Error, Forward Hint fail-closed, and projection ports remain frozen.

## P1 — Align Y-Up handedness

`Align Points To Surface Normals` builds frames via [`FrameUtils.fromNormalUpAxis`](../src/main/java/com/nodecraft/nodesystem/util/FrameUtils.java):

| Local Up | X | Y | Z |
|----------|---|---|---|
| X | normal | tangent | `normal × tangent` |
| Y | tangent | normal | `tangent × normal` |
| Z | tangent | `normal × tangent` | normal |

Y-Up previously used `Z = normal × tangent`, which disagreed with `FrameData.orthonormal` (`Z = X × Y`) and flipped Local Y to **-Normal**. Planes still used `+Normal` — Plane vs Frame contradicted.

Contracts lock `selectedAxis · normal > 0.999999` and right-handed `X × Y ≈ Z` for X/Y/Z.

## P1-small — Project Path To Plane

After projection:

1. Adjacent-dedupe coincident vertices
2. Fail if fewer than 2 distinct points
3. Fail if `PathUtils.hasDegenerateSegment`
4. Emit via `PathUtils.toPathData` (2 points → LINE; else POLYLINE)

## P2

- **Rotate Vector** emits canonical `VectorData` via `VectorUtils.toVectorPort`
- **Project Profile** checks `MAX_LIST_ELEMENTS` (defense; `PolygonProfileData` already caps at `MAX_PROFILE_VERTICES`)

## Migration

V98 → V99 is a no-op (runtime semantics only; no wire/port changes).

## Verification

```text
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.OrientationLanguageContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.TransformFamilyContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.GraphFormatVersionContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.GeometryCurvesFamilyContractTest"
```
