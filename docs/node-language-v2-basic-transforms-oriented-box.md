# Node Language v2 — Basic Transforms / Oriented Box Consistency

**Status: PASSED / FROZEN** (Graph **V98**)

Remediation over [node-language-v1-basic-transforms.md](node-language-v1-basic-transforms.md) (V52 inventory and contracts remain frozen).

## Problem

`GeometryTransform` / `GeometryMirror` previously preserved `BoxGeometryData.isOriented()` from the source box while updating `orientationMatrix`. After Rotate / Transform-with-rotation / Mirror of an axis-aligned box:

- `getCorners()` applied the new orientation
- `GeometryBoundsResolver` and `GeometryVoxelizer` ignored it when `oriented == false`

That split the Create Box → Rotate → Bounds / Preview / Voxelize / Build chain.

## Invariant (V98)

```text
BoxGeometryData.oriented must match whether Bounds / Voxelize should use oriented code paths.

GeometryTransform (box):
  oriented = source.isOriented() || !isIdentity(rotationMatrix)

GeometryMirror (box):
  oriented = true   (always; same as Ellipsoid mirror)
```

Move / uniform Scale with identity rotation leave an axis-aligned box unoriented.

## Scope

Shared utilities only — no inventory, port, or TRS-order changes.

| Change | Location |
|--------|----------|
| Oriented flag on transform | `GeometryTransform` box branch |
| Oriented flag on mirror | `GeometryMirror` box branch |
| Unsupported transform hides gizmo | `TransformGeometryNode` |
| Frame-list size pre-budget | `TransformPointsByFramesNode` |

## Migration

V97 → V98 is a no-op (runtime representation fix; existing wires unchanged).

## Verification

```text
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.BasicTransformsLanguageV2ContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.BasicTransformsLanguageContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.TransformFamilyContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.GraphFormatVersionContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.GeometryVoxelLanguageContractTest"
```
