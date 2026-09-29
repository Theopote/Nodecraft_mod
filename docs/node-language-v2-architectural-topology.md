# Architectural Topology & Path Join — Node Language v2

**Status: Path Join PASSED / FROZEN; Roof Topology remediation in progress** (Graph **V97**)

Focused remediation for roof topology outputs and path join semantics. The V68 inventory (18 nodes), strict input, Valid/Error, and instance budgets remain frozen under [node-language-v1-architectural-primitives.md](node-language-v1-architectural-primitives.md).

## Roof topology (Roof Base + Roof Generator)

### New outputs (additive)

| Port | Type | Content |
|------|------|---------|
| Primary Eave Path | PATH | Longest eave edge (backward compatible) |
| Primary Ridge Path | PATH | Primary ridge when present |
| Eaves | PATH_LIST | All perimeter eave edges |
| Ridges | PATH_LIST | All ridge segments (type-specific) |
| Valleys | PATH_LIST | Valley segments when applicable (empty otherwise) |

### Type-specific topology

| Roof type | Ridges | Valleys |
|-----------|--------|---------|
| flat / shed | empty | empty |
| gable | 1 full ridge | empty |
| asymmetric_gable | 2 ridges (extrusion edges at left/right peaks) | empty |
| hip | 1 ridge (profile ridge edge shared with geometry) | empty |
| cross_gable | 2 perpendicular ridges (extrusion edges) | 1 valley (primary/secondary roof plane intersection) |
| m | 2 ridges (extrusion edges at left/right peaks) | 1 valley (extrusion edge at center valley point) |

Primary paths are derived from topology lists (not independent gable approximations).

## Path join (Wall Along Path + Railing)

### Join input

| Value | Behavior |
|-------|----------|
| miter (default) | Parallel offset with miter corner intersections |
| bevel | Offset with miter limit fallback |
| butt | Per-segment offset without corner extension |

### Signed Offset

Wall, Beam, and Railing **Offset** ports accept any finite DOUBLE (negative = opposite side).

### Wall Along Path v2

Algorithm: planar path → joined centerline offset → left/right footprint → vertical extrusion (`PrismGeometryData`, composite when concave footprint decomposes).

Requires planar polyline (horizontal XZ within tolerance). **Count** = number of extrusion pieces.

### Railing v2

Builds joined offset centerline first; posts sampled evenly on offset path; rails follow offset segments.

### Beam Along Path

Unchanged segment semantics: **one box per path edge** (not a continuous sweep). Signed offset only.

## Migration (V96 → V97)

Additive ports only; existing `output_eave_path` / `output_ridge_path` wires preserved.

## Verification

```text
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.ArchitecturalTopologyV2ContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.ArchitecturalPathFollowingContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.ArchitecturalPrimitivesLanguageContractTest"
```
