# Architectural Topology & Path Join — Node Language v2

**Status: Path Join PASSED / FROZEN; Roof Faces additive** (historical Graph **V97**; `GraphFormatVersion.CURRENT` is stamp-only **1**)

Focused remediation for roof topology outputs and path join semantics. The V68 inventory (now 19 nodes including Window Frame), strict input, Valid/Error, and instance budgets remain frozen under [node-language-v1-architectural-primitives.md](node-language-v1-architectural-primitives.md).

## Roof topology (Roof Base + Roof Generator)

### Outputs (additive)

| Port | Type | Content |
|------|------|---------|
| Primary Eave Path | PATH | Longest eave edge (backward compatible) |
| Primary Ridge Path | PATH | Primary ridge when present |
| Eaves | PATH_LIST | All perimeter eave edges |
| Ridges | PATH_LIST | All ridge segments (type-specific) |
| Valleys | PATH_LIST | Valley segments when applicable (empty otherwise) |
| Faces | PLANAR_REGION_LIST | Core roof planes (flat 1, shed 1, gable 2). Specialty types may be empty. |
| Slope Directions | VECTOR_LIST | Downslope unit vectors aligned with Faces (flat = zero) |

Invalid list ports are empty lists. Derived roof width/depth that are non-finite fail closed before geometry.

### Type-specific topology

| Roof type | Ridges | Valleys | Faces |
|-----------|--------|---------|-------|
| flat | empty | empty | 1 footprint rectangle |
| shed | empty | empty | 1 slope quad |
| gable | 1 full ridge | empty | 2 slope quads |
| asymmetric_gable | 2 ridges (extrusion edges at left/right peaks) | empty | empty |
| hip | 1 ridge (profile ridge edge shared with geometry) | empty | empty |
| cross_gable | 2 perpendicular ridges (extrusion edges) | 1 valley (primary/secondary roof plane intersection) | empty |
| m | 2 ridges (extrusion edges at left/right peaks) | 1 valley (extrusion edge at center valley point) | empty |

Primary paths are derived from topology lists (not independent gable approximations). Core Faces fail closed if `PlanarRegionData.tryCreate` cannot build a plane.

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

Requires planar polyline (horizontal XZ within tolerance). **Height is world +Y.** **Count** = number of extrusion pieces.

**Bottom Centerline** (`output_bottom_path`) is the resolved base after Offset. **Center Line** (`output_center_line`) is an additive alias of the same path.

### Railing v2

Builds joined offset centerline first; posts sampled evenly on offset path; rails follow offset segments.

### Beam Along Path

Unchanged segment semantics: **one box per path edge** (not a continuous sweep). Signed offset only.

## Migration (V96 → V97 residue)

Additive ports only; existing `output_eave_path` / `output_ridge_path` / `output_bottom_path` wires preserved. No graph format bump.

## Verification

```text
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.ArchitecturalTopologyV2ContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.ArchitecturalPathFollowingContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.ArchitecturalPrimitivesLanguageContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.ArchitecturalRoofGeometryContractTest"
```
