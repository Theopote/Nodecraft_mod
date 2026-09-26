# NodeCraft 节点库

- **统计范围**：`src/main/java/com/nodecraft/nodesystem/nodes`
- **节点总数**：**532**
- **分类总数**：**60**
- **说明**：由 `node-catalog.json`（`generateNodeCatalog`）自动生成；「节点名称」与「说明」取自 `@NodeInfo`（与编辑器一致）。空说明显示为 `-`。

## 分类统计

| 分类 ID | 节点数 |
|---|---:|
| `flow.control` | 3 |
| `flow.loop` | 3 |
| `geometry.analysis` | 2 |
| `geometry.architectural_primitives` | 20 |
| `geometry.boolean` | 2 |
| `geometry.combine` | 1 |
| `geometry.curves` | 28 |
| `geometry.primitives` | 29 |
| `geometry.profiles` | 24 |
| `geometry.sdf` | 13 |
| `geometry.solids` | 24 |
| `geometry.voxel` | 1 |
| `input.context` | 4 |
| `input.numeric` | 10 |
| `input.type_selectors` | 4 |
| `input.values` | 6 |
| `material.basic_assignment` | 4 |
| `material.block_state` | 4 |
| `material.directional_mapping` | 3 |
| `material.gradient_mapping` | 5 |
| `material.pattern_mapping` | 4 |
| `material.surface_aging` | 3 |
| `math.compare` | 6 |
| `math.data_tree` | 14 |
| `math.fields` | 17 |
| `math.list` | 23 |
| `math.logic` | 6 |
| `math.random` | 6 |
| `math.scalar_math` | 23 |
| `math.sequence` | 3 |
| `math.trigonometry` | 10 |
| `math.vector` | 1 |
| `output.debug` | 4 |
| `output.execute` | 8 |
| `output.export` | 4 |
| `output.preview` | 12 |
| `pattern.grid` | 5 |
| `pattern.linear` | 4 |
| `pattern.lsystem` | 3 |
| `pattern.radial` | 3 |
| `pattern.surface_volume_distribution` | 6 |
| `pattern.voronoi_3d` | 1 |
| `reference.frames` | 8 |
| `reference.planes` | 7 |
| `reference.points` | 19 |
| `reference.vectors` | 17 |
| `transform.basic_transforms` | 9 |
| `transform.deformations` | 11 |
| `transform.orientation` | 6 |
| `transform.placement` | 8 |
| `utilities.assist` | 5 |
| `utilities.fileio` | 3 |
| `utilities.morphology` | 1 |
| `utilities.organization` | 7 |
| `variable` | 6 |
| `world.query` | 11 |
| `world.read` | 12 |
| `world.selection` | 9 |
| `world.terrain` | 19 |
| `world.write` | 18 |

## flow.control（3）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Branch | `flow.control.branch` | Routes data and exec flow by condition. Wire exec_true/exec_false for branch skipping; legacy data outputs still work in dataflow graphs. | `BranchNode` |
| Sequence | `flow.control.sequence` | Replicates a signal across steps. Wire exec_step_N for ordered step-by-step execution; legacy data outputs remain for dataflow graphs. | `SequenceNode` |
| Do Once | `flow.control.do_once` | Passes exec/data once per run unless reset. Wire exec_out for first pass and exec_blocked for repeats. | `DoOnceNode` |

## flow.loop（3）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| For Each Loop | `flow.loop.for_each` | Expands a list into items. Wire exec_body for per-item side effects; legacy list outputs remain for dataflow graphs. | `ForEachLoopNode` |
| Accumulator | `flow.loop.accumulator` | Accumulates list values into a single result. | `AccumulatorNode` |
| While Loop | `flow.loop.while` | Routes exec flow while condition is true. Wire exec_body for loop body and loop exec back to exec_in; exec_complete fires when condition is false. | `WhileLoopNode` |

## geometry.analysis（2）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Bounding Box | `geometry.boolean.bounding_box` | Calculates an axis-aligned bounding box from a block list or region | `BoundingBoxNode` |
| Geometry Bounds | `geometry.boolean.geometry_bounds` | Calculates an axis-aligned bounding box from any supported geometry | `GeometryBoundsNode` |

## geometry.architectural_primitives（20）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Window Array | `geometry.architectural_primitives.window_array` | Generates a rectangular array of inset window opening boxes on a box face | `WindowArrayNode` |
| Door Array | `geometry.architectural_primitives.door_array` | Generates a rectangular array of inset door openings with placement frames | `DoorArrayNode` |
| Column Grid | `geometry.architectural_primitives.column_grid` | Generates a rectangular grid of columns with base/top points and placement frames | `ColumnGridNode` |
| Railing | `geometry.architectural_primitives.railing` | Generates a railing or balustrade that follows a path (line, polyline, or curve) | `RailingNode` |
| Roof Base | `geometry.architectural_primitives.roof_base` | Generates a core roof (flat, shed, or gable) from a box face footprint | `RoofBaseNode` |
| Staircase | `geometry.architectural_primitives.staircase` | Generates architectural staircases from a path | `StaircaseNode` |
| Roof Generator | `geometry.architectural_primitives.roof_generator` | Advanced roof convenience (specialty shapes); prefer Roof Base for flat/shed/gable | `RoofGeneratorNode` |
| Facade Panel Array | `geometry.architectural_primitives.facade_panel_array` | Generates a rectangular array of facade panels on a box face | `FacadePanelArrayNode` |
| Arch Opening | `geometry.architectural_primitives.arch_opening` | Generates a rectangular, round, or pointed arch opening volume | `ArchOpeningNode` |
| Wall With Openings | `geometry.architectural_primitives.wall_with_openings` | Generates a wall slab and separate opening volumes (use Difference to cut holes) | `WallWithOpeningsNode` |
| Pilaster / Cornice | `geometry.architectural_primitives.pilaster_cornice` | Generates pilasters and a cornice along a box face | `PilasterOrCorniceNode` |
| Array Along Curve | `geometry.architectural_primitives.array_along_curve` | Places repeated columns, posts, or panels along a curve or polyline path | `ArrayAlongCurveNode` |
| Deconstruct Architectural Opening | `geometry.architectural_primitives.deconstruct_opening` | Flattens architectural opening geometry into component lists and bounds | `DeconstructArchitecturalOpeningNode` |
| Floor Slab | `geometry.architectural_primitives.floor_slab` | Generates a floor slab from a box face footprint | `FloorSlabNode` |
| Floor Slab With Beams | `geometry.architectural_primitives.floor_slab_with_beams` | Convenience: floor slab plus support beam grid (prefer Floor Slab + Beam Grid) | `FloorSlabWithBeamsNode` |
| Beam Grid | `geometry.architectural_primitives.beam_grid` | Generates a support beam grid on a box face footprint | `BeamGridNode` |
| Molding Profile | `geometry.architectural_primitives.molding_profile` | Generates decorative molding cross-section profiles | `MoldingProfileNode` |
| Wall Along Path | `geometry.architectural_primitives.wall_along_path` | Generates continuous wall slabs along a path (line, polyline, or curve) | `WallAlongPathNode` |
| Beam Along Path | `geometry.architectural_primitives.beam_along_path` | Generates structural beams along a path (line, polyline, or curve) | `BeamAlongPathNode` |
| Column | `geometry.architectural_primitives.column` | Generates a single column from a frame or base point | `ColumnNode` |

## geometry.boolean（2）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Difference | `geometry.boolean.difference` | Subtracts cutter geometry when voxelized/built. Result is evaluated on the Minecraft block grid (deferred voxel boolean, not analytic BRep). | `DifferenceNode` |
| Intersection | `geometry.boolean.intersection` | Keeps overlapping voxelized blocks from both geometries when built. Deferred voxel boolean on the Minecraft block grid (not analytic BRep). | `IntersectionNode` |

## geometry.combine（1）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Combine Geometry | `geometry.combine.geometry` | Structural grouping of geometries into a composite. Bake/voxelize merges blocks (set union); not an analytic BRep union or SDF smooth union. | `GeometryUnionNode` |

## geometry.curves（28）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Points To Path | `geometry.curves.points_to_path` | Builds a line or polyline from an ordered point list | `PointsToPathNode` |
| Extract Path Points | `geometry.curves.path_to_points` | Extracts existing vertices/sample points from a path. Does not resample — use Resample Path for that. | `PathToPointsNode` |
| Arc | `geometry.curves.arc` | Builds a sampled circular arc from a center point, plane, radius, and start/end angles | `ArcNode` |
| Face Edge To Path | `geometry.curves.edge_to_curve` | Converts a face edge into line, polyline, and point outputs for path workflows | `FaceEdgeToPathNode` |
| Bezier | `geometry.curves.bezier` | Builds a sampled Bezier curve from an ordered list of control points | `BezierNode` |
| Box Face Boundary Path | `geometry.curves.face_boundary_curve` | Builds a closed boundary path from a box face for preview and downstream path workflows | `BoxFaceBoundaryPathNode` |
| Join Paths | `geometry.curves.join_paths` | Joins two paths when Path A end meets Path B start within tolerance. Does not bridge or reverse. | `JoinPathsNode` |
| Reverse Path | `geometry.curves.reverse_path` | Reverses the direction of a path. | `ReversePathNode` |
| Split Path | `geometry.curves.split_path` | Splits a path at a normalized parameter into two path segments. | `SplitPathNode` |
| Trim Path | `geometry.curves.trim_path` | Extracts a sub-path between two normalized parameters. | `TrimPathNode` |
| Explode Path | `geometry.curves.explode_path` | Decomposes a path into per-segment paths as PATH_LIST. | `ExplodePathNode` |
| Extend Path | `geometry.curves.extend_path` | Linearly extends an open path along start/end tangents by the given lengths. | `ExtendPathNode` |
| Interpolate Spline | `geometry.curves.interpolate_spline` | Builds a Catmull-Rom interpolation spline that passes through all resolved input points | `InterpolateSplineNode` |
| B-Spline | `geometry.curves.b_spline` | Builds a sampled clamped uniform B-spline from an ordered control point list | `BSplineNode` |
| Fillet Path Corners | `geometry.curves.fillet_polyline_corners` | Fillets interior corners of an open path with circular arcs in the work plane | `PolylineCornerFilletNode` |
| Offset Path In Plane | `geometry.curves.offset_curve_plane` | Offsets a path (line, polyline, or curve) in a work plane by signed distance. | `OffsetCurveInPlaneNode` |
| NURBS Curve | `geometry.curves.nurbs` | Builds a sampled clamped uniform NURBS curve from control points and optional per-point weights | `NurbsCurveNode` |
| Rainbow Curve Offset | `geometry.curves.rainbow_curve_offset` | Generates multiple parallel offset polylines around a space curve using path frames. | `RainbowCurveOffsetNode` |
| Resample Path | `geometry.curves.resample_path` | Resamples a path along arc length by Count or Spacing. Primary output is PATH. | `ResamplePolylineByLengthNode` |
| Path Length | `geometry.curves.path_length` | Computes the total length of a line, polyline, or curve path | `PolylineLengthNode` |
| Evaluate Path | `geometry.curves.evaluate_curve` | Evaluates a path at normalized parameter t and outputs point and tangent. | `CurveEvaluateNode` |
| Closest Point On Path | `geometry.curves.closest_point_on_path` | Finds the closest point on a path to a query point. | `ClosestPointOnPathNode` |
| Parabola On Plane | `geometry.curves.parabola_curve` | Builds a sampled parabola on a plane from vertex, curvature, x-range, and segment count | `ParabolaOnPlaneNode` |
| Helix Curve | `geometry.curves.helix` | Builds a sampled helix from center, axis, radius, pitch, turns, and segment count. | `HelixCurveNode` |
| Infinity Curve On Plane | `geometry.curves.infinity_curve` | Builds a sampled figure-eight (lemniscate-like) curve on a plane | `InfinityCurveOnPlaneNode` |
| Voxelize Path | `geometry.curves.voxelize_curve` | Converts a path directly into voxel block coordinates using cylindrical path segments. | `VoxelizeCurveNode` |
| Blend Paths | `geometry.curves.blend_curves` | Creates a smooth transition path between two path endpoints. | `BlendCurvesNode` |
| Tween Paths | `geometry.curves.tween_curves` | Creates evenly spaced intermediate paths between two path inputs. | `TweenCurvesNode` |

## geometry.primitives（29）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Box by Center + Size | `geometry.primitives.box` | Constructs continuous box geometry from a center point and X/Y/Z sizes. Blocks/Region remain legacy convenience outputs. | `BoxCenterSizeNode` |
| Box by Corner + Size | `geometry.primitives.box_from_corner_size` | Generates a box from one anchor corner and signed X/Y/Z sizes. Negative values grow in the opposite local axis direction. | `BoxCornerSizeNode` |
| Box by Two Corners | `geometry.primitives.box_from_corners` | Generates an axis-aligned box from two opposite corner points | `BoxCornersNode` |
| Sphere By Center Radius | `geometry.primitives.sphere` | Constructs sphere geometry from a center point and radius | `SphereByCenterRadiusNode` |
| Sphere By Diameter | `geometry.primitives.sphere_from_diameter` | Constructs sphere geometry from two diameter endpoints | `SphereByDiameterNode` |
| Cylinder By Axis Radius | `geometry.primitives.cylinder` | Constructs cylinder geometry from two axis endpoints and a radius | `CylinderByAxisRadiusNode` |
| Torus By Center Axis Radii | `geometry.primitives.torus` | Constructs torus geometry from a center point, symmetry axis direction, major radius, and tube (minor) radius | `TorusByCenterAxisRadiiNode` |
| Frustum By Two Centers Radii | `geometry.primitives.frustum_cone` | Constructs a circular frustum from two parallel face centers and their radii (set top radius to 0 for a cone) | `FrustumByTwoCentersRadiiNode` |
| Cone By Base Apex Radius | `geometry.primitives.cone` | Constructs cone geometry from a base center, apex point, and base radius | `ConeByBaseApexRadiusNode` |
| Ellipsoid By Center Radii | `geometry.primitives.ellipsoid` | Constructs ellipsoid geometry from a center point and X/Y/Z radii | `EllipsoidByCenterRadiiNode` |
| Deconstruct Box Geometry | `geometry.primitives.deconstruct_box` | Extracts center, half extents, orientation, corners, and faces from box geometry | `DeconstructBoxGeometryNode` |
| Octahedron By Center Size | `geometry.primitives.octahedron` | Constructs octahedron geometry from a center point, vertex radius, and optional orientation | `OctahedronByCenterSizeNode` |
| Deconstruct Sphere | `geometry.primitives.deconstruct_sphere` | Extracts center, radius, diameter, bounds, area, and volume from sphere geometry | `DeconstructSphereNode` |
| Tetrahedron By Center Edge | `geometry.primitives.tetrahedron` | Constructs tetrahedron geometry from a center point, edge length, and optional orientation | `TetrahedronByCenterEdgeNode` |
| Deconstruct Cylinder | `geometry.primitives.deconstruct_cylinder` | Extracts axis, radius, height, bounds, and analytical values from cylinder geometry | `DeconstructCylinderNode` |
| Deconstruct Cone | `geometry.primitives.deconstruct_cone` | Extracts axis, height, radius, bounds, and analytical values from cone geometry | `DeconstructConeNode` |
| Hemisphere By Center Axis Radius | `geometry.primitives.hemisphere` | Constructs a solid hemisphere: sphere intersected with the half-space on the +axis side of the center (flat face through center, dome along axis) | `HemisphereByCenterAxisRadiusNode` |
| Capsule By Axis Radius | `geometry.primitives.capsule` | Constructs analytic capsule geometry from axis endpoints and radius (cylinder + two hemispheres). | `CapsuleByAxisRadiusNode` |
| Deconstruct Frustum Cone | `geometry.primitives.deconstruct_frustum_cone` | Extracts axis, heights, radii, bounds, and analytical values from frustum cone geometry | `DeconstructFrustumConeNode` |
| Square Pyramid | `geometry.primitives.square_pyramid` | Constructs square pyramid geometry from a base center, base size, height, and plane | `SquarePyramidNode` |
| Deconstruct Ellipsoid | `geometry.primitives.deconstruct_ellipsoid` | Extracts center, radii, bounds, volume, and approximate surface area from ellipsoid geometry | `DeconstructEllipsoidNode` |
| Deconstruct Octahedron | `geometry.primitives.deconstruct_octahedron` | Extracts center, size, vertices, bounds, and analytical values from octahedron geometry | `DeconstructOctahedronNode` |
| Deconstruct Tetrahedron | `geometry.primitives.deconstruct_tetrahedron` | Extracts center, edge length, vertices, bounds, and analytical values from tetrahedron geometry | `DeconstructTetrahedronNode` |
| Deconstruct Prism | `geometry.primitives.deconstruct_prism` | Extracts base polygon, top polygon, extrusion, side surface strip, and bounds from prism geometry | `DeconstructPrismNode` |
| Deconstruct Hemisphere | `geometry.primitives.deconstruct_hemisphere` | Extracts center, axis, radius, bounds, and analytical values from hemisphere geometry | `DeconstructHemisphereNode` |
| Icosahedron By Center Edge | `geometry.primitives.icosahedron` | Constructs a regular icosahedron from a center point, edge length, and optional orientation | `IcosahedronByCenterEdgeNode` |
| Dodecahedron By Center Edge | `geometry.primitives.dodecahedron` | Constructs a regular dodecahedron from a center point, edge length, and optional orientation | `DodecahedronByCenterEdgeNode` |
| Deconstruct Icosahedron | `geometry.primitives.deconstruct_icosahedron` | Extracts center, edge length, vertices, bounds, and analytical values from icosahedron geometry | `DeconstructIcosahedronNode` |
| Deconstruct Dodecahedron | `geometry.primitives.deconstruct_dodecahedron` | Extracts center, edge length, vertices, bounds, and analytical values from dodecahedron geometry | `DeconstructDodecahedronNode` |

## geometry.profiles（24）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Rectangle On Plane | `geometry.profiles.rectangle_profile` | Constructs a planar rectangle from width, height, and an optional center/plane (defaults to XZ) | `RectangleOnPlaneNode` |
| Regular Polygon On Plane | `geometry.profiles.polygon_profile` | Constructs a regular polygon from radius, sides, and an optional center/plane (defaults to XZ) | `RegularPolygonOnPlaneNode` |
| Polygon By Points | `geometry.profiles.custom_profile` | Constructs a planar polygon profile from an ordered point list | `PolygonByPointsNode` |
| Resample Polygon Profile | `geometry.profiles.resample_profile` | Resamples a polygon profile to a target edge count using perimeter-distance sampling | `ResamplePolygonProfileNode` |
| Deconstruct Polygon Profile | `geometry.profiles.deconstruct_profile` | Extracts points, boundary, plane, center, perimeter, and area from a polygon profile | `DeconstructPolygonProfileNode` |
| Convex Hull 2D On Plane | `geometry.profiles.convex_hull_plane` | Projects points into a plane, computes their 2D convex hull, and outputs a closed polygon profile | `ConvexHull2DOnPlaneNode` |
| Voronoi Cells 2D On Plane | `geometry.profiles.voronoi_cells_plane` | Projects sites into a plane, builds a clipped planar Voronoi diagram (JTS), and outputs each cell as a polygon profile on the plane | `VoronoiCells2DOnPlaneNode` |
| Convex Hull 3D From Points | `geometry.profiles.convex_hull_3d_points` | Builds a 3D convex hull (triangle facets) from points; intended for small clouds due to brute-force enumeration; coplanar / collinear inputs yield no facets | `ConvexHull3DFromPointsNode` |
| Circle On Plane | `geometry.profiles.circle_profile` | Constructs a circular profile from radius and an optional center/plane (defaults to XZ) | `CircleOnPlaneNode` |
| Ellipse On Plane | `geometry.profiles.ellipse_profile` | Constructs an ellipse profile from center, major/minor radii, plane, and segment count (defaults to XZ) | `EllipseOnPlaneNode` |
| Sector On Plane | `geometry.profiles.sector_profile` | Constructs a circular sector profile from center, radius, start/end angles, and plane | `SectorOnPlaneNode` |
| Annulus On Plane | `geometry.profiles.annulus_profile` | Constructs annulus boundaries from center, inner/outer radii, plane, and segment count | `AnnulusOnPlaneNode` |
| Rounded Rectangle On Plane | `geometry.profiles.rounded_rectangle_profile` | Constructs a rounded-rectangle profile from center, width, height, corner radius, and plane (defaults to XZ) | `RoundedRectangleOnPlaneNode` |
| Star Polygon On Plane | `geometry.profiles.star_polygon_profile` | Constructs a star polygon profile from center, inner/outer radii, point count, and plane (defaults to XZ) | `StarPolygonOnPlaneNode` |
| SemiCircle On Plane | `geometry.profiles.semicircle_profile` | Constructs a semicircle profile from center, radius, plane, and segment count (defaults to XZ) | `SemiCircleOnPlaneNode` |
| Rhombus On Plane | `geometry.profiles.rhombus_profile` | Constructs a rhombus profile from center, horizontal diagonal, vertical diagonal, and plane (defaults to XZ) | `RhombusOnPlaneNode` |
| Capsule On Plane | `geometry.profiles.capsule_profile` | Constructs a capsule (stadium) profile from center, length, radius, and plane | `CapsuleOnPlaneNode` |
| Heart On Plane | `geometry.profiles.heart_profile` | Constructs a heart profile from center, width, height, plane, and segment count (defaults to XZ) | `HeartOnPlaneNode` |
| Annular Sector On Plane | `geometry.profiles.annular_sector_profile` | Constructs an annular sector boundary from center, inner/outer radii, angle range, and plane (defaults to XZ) | `AnnularSectorOnPlaneNode` |
| Cross On Plane | `geometry.profiles.cross_profile` | Constructs a plus-shaped cross profile from arm length, arm width, center, and plane | `CrossOnPlaneNode` |
| Gear On Plane | `geometry.profiles.gear_profile` | Constructs a gear-like profile from center, tooth count, root/tip radii, and plane (defaults to XZ) | `GearOnPlaneNode` |
| Profile Offset In Plane | `geometry.profiles.offset_profile_plane` | Offsets a polygon profile in its plane by signed distance using 2D buffer logic | `ProfileOffsetInPlaneNode` |
| Profile Boolean 2D | `geometry.profiles.boolean_2d` | Performs 2D boolean operations (union/intersection/difference) on two polygon profiles in a shared plane | `ProfileBoolean2DNode` |
| Profile Triangulate 2D | `geometry.profiles.triangulate_2d` | Triangulates a planar polygon profile into triangle profiles using ear clipping | `ProfileTriangulate2DNode` |

## geometry.sdf（13）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| SDF Sphere | `geometry.boolean.sdf_sphere` | Builds a sphere signed-distance-field primitive from center and radius | `SdfSphereNode` |
| SDF Box | `geometry.boolean.sdf_box` | Builds an axis-aligned box signed-distance-field primitive from center and half extents | `SdfBoxNode` |
| SDF Capsule | `geometry.boolean.sdf_capsule` | Builds a capsule signed-distance-field primitive from segment endpoints and radius | `SdfCapsuleNode` |
| SDF Torus | `geometry.boolean.sdf_torus` | Builds a torus signed-distance-field primitive around the Y axis from center and radii | `SdfTorusNode` |
| SDF Boolean | `geometry.boolean.sdf_boolean` | Combines two SDF inputs with union/intersection/difference and optional smooth blending | `SdfBooleanNode` |
| SDF To Geometry | `geometry.boolean.sdf_to_geometry` | Wraps an SDF into GeometryData with explicit or auto-estimated sampling bounds for block baking | `SdfToGeometryNode` |
| SDF Sample Point | `geometry.boolean.sdf_sample_point` | Samples signed distance at a query point and reports inside/outside state | `SdfSamplePointNode` |
| SDF Sample Points | `geometry.boolean.sdf_sample_points` | Samples signed distance for each query point and outputs distance and inside lists | `SdfSamplePointsNode` |
| SDF Gradient At Point | `geometry.boolean.sdf_gradient_point` | Samples SDF gradient at a point and outputs a normalized normal-like direction | `SdfGradientPointNode` |
| SDF Noise Displace | `geometry.boolean.sdf_noise_displace` | Applies deterministic pseudo-noise displacement to an input SDF | `SdfNoiseDisplaceNode` |
| SDF Transform | `geometry.boolean.sdf_transform` | Applies translation, rotation, and uniform scale to an input SDF | `SdfTransformNode` |
| SDF Blend Material Mask | `geometry.boolean.sdf_blend_material_mask` | Maps SDF distance values to smooth 0..1 blend weights and inside/outside booleans | `SdfBlendMaterialMaskNode` |
| SDF Domain Warp | `geometry.boolean.sdf_domain_warp` | Applies coordinate-space noise warping before sampling an input SDF | `SdfDomainWarpNode` |

## geometry.solids（24）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Extrude | `geometry.solids.extrude` | Extrudes a polygon profile by a direction vector into prism geometry (canonical Extrude) | `ExtrudeProfileNode` |
| Extrude Point List | `geometry.solids.extrude_from_points` | Extrudes an ordered point list by a direction vector and emits source path, top path, and side segments | `ExtrudePointListNode` |
| Extrude Box Face | `geometry.solids.extrude_box_face` | Extrudes a box face into a new box segment and returns a composite geometry | `ExtrudeBoxFaceNode` |
| Loft Surface | `geometry.solids.loft` | Lofts two polygon profiles into a SURFACE_STRIP (surface topology, not a solid). Auto-resamples when vertex counts differ. | `LoftProfilesNode` |
| Loft Point Lists | `geometry.solids.loft_from_points` | Connects two ordered point lists and emits source paths, target paths, and loft rail segments | `LoftPointListsNode` |
| Sweep Surface | `geometry.solids.sweep` | Sweeps a polygon profile along a path into a SURFACE_STRIP (surface topology, not a solid). Use Surface Strip To Lattice for wireframe preview geometry. | `SweepProfileAlongPathNode` |
| Sweep Surface From Points | `geometry.solids.sweep_from_points` | Sweeps an ordered point profile along a path into a SURFACE_STRIP (surface topology, not a solid) | `SweepPointListAlongPathNode` |
| Revolve Profile | `geometry.solids.revolve` | Revolves a polygon profile around an axis and emits section profiles plus a side surface strip | `RevolveProfileNode` |
| Surface Strip To Lattice | `geometry.solids.surface_strip_to_lattice` | Approximates a surface strip as a cylinder lattice (section edges + rails). Not a filled solid or closed shell. | `SurfaceStripToGeometryNode` |
| Sweep 2 Rails | `geometry.solids.sweep_two_rails` | Sweeps a profile between two guide rails with optional scale and rotation controls | `SweepTwoRailsNode` |
| Push/Pull Box Face | `geometry.solids.push_pull_face` | Moves one box face along its normal and outputs a new box geometry | `PushPullBoxFaceNode` |
| Shell Surface Strip | `geometry.solids.shell` | Builds inner and outer offset shell layers from a surface strip and emits cap strips plus an optional geometry approximation | `ShellNode` |
| Thicken Surface | `geometry.solids.thicken_surface` | Thickens a surface strip into two offset layers with optional cap strips and a reusable geometry approximation | `ThickenSurfaceNode` |
| Contour | `geometry.solids.contour` | Generates parallel section planes and traces voxel contour profiles from geometry at regular spacing. | `ContourNode` |
| Prism By Base Points Vector | `geometry.solids.extrude_profile_from_points` | Constructs prism geometry from an ordered base polygon and an extrusion vector | `PrismByBasePointsVectorNode` |
| Section Cut | `geometry.solids.section_cut` | Cuts geometry by one or more planes and traces voxel slice contours as section profiles, boundaries, blocks, and tree-grouped data. | `SectionCutNode` |
| Deconstruct Surface Strip | `geometry.solids.deconstruct_surface_strip` | Breaks a surface strip into section paths, flattened points, and rail segments | `DeconstructSurfaceStripNode` |
| Multi-Section Loft Surface | `geometry.solids.loft_multi_section` | Lofts multiple polygon sections into one SURFACE_STRIP (surface topology, not a solid) with close, flip, seam, and resample options. | `MultiSectionLoftNode` |
| Extract Surface Strip Range | `geometry.solids.extract_surface_strip_range` | Extracts a contiguous section range from a surface strip as a smaller surface strip | `ExtractSurfaceStripRangeNode` |
| Morph Between Profiles | `geometry.solids.morph_profiles` | Interpolates between two compatible polygon profiles using parameter t in [0,1]. | `MorphBetweenProfilesNode` |
| Offset Surface Strip | `geometry.solids.offset_surface_strip` | Offsets a surface strip by a signed distance and outputs a single offset surface | `OffsetSurfaceStripNode` |
| Shrinkwrap Points On Surface Strip | `geometry.solids.shrinkwrap_points_surface_strip` | Projects each query point to the closest location on the surface strip triangle mesh | `ShrinkwrapPointsOnSurfaceStripNode` |
| Shrinkwrap Points To Voxel Geometry | `geometry.solids.shrinkwrap_points_voxel_geometry` | Voxelizes geometry to blocks, then snaps each query point to the nearest voxel block center (shell when fill is off); distinct from triangle strip shrinkwrap | `ShrinkwrapPointsToVoxelGeometryNode` |
| Prism By Profile Vector | `geometry.solids.extrude_profile` | Legacy/advanced prism construction from profile + extrusion vector. Prefer Extrude (geometry.solids.extrude) for new graphs. | `PrismByProfileVectorNode` |

## geometry.voxel（1）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Voxelize Geometry | `geometry.voxel.voxelize_geometry` | Converts geometry into Minecraft block coordinates (BLOCK_LIST). Pure conversion — does not write the world. Use Apply Changes to place. | `VoxelizeGeometryNode` |

## input.context（4）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Player Position Snapshot | `input.context.player_position` | Captures a stable continuous player world position snapshot. Click Update Position to recapture. | `PlayerPositionNode` |
| Player Raycast | `input.context.player_raycast` | Raycasts from the player view and reports hit position, block, entity, and distance. | `PlayerRaycastNode` |
| Dimension Info | `input.context.dimension_info` | Gets the current dimension and basic dimension traits from the active Minecraft world. | `DimensionInfoNode` |
| Current Time | `input.context.current_time` | Gets the current time and weather state from the active Minecraft world. | `CurrentTimeNode` |

## input.numeric（10）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Integer Input | `input.numeric.integer` | 允许手动输入整数值的节点 | `IntegerInputNode` |
| Number Input | `input.numeric.float` | 精确浮点值输入。Min/Max 可选。需要快速有界探索时使用 Number Slider。 | `FloatInputNode` |
| Integer Slider | `input.numeric.integer_slider` | 输出一个可通过滑动条调节的整数值 | `IntegerSliderNode` |
| Number Slider | `input.numeric.float_slider` | 有界参数探索：精确 double 输入 + 滑动条快速调节。Min/Max 必须设置。 | `FloatSliderNode` |
| Angle Slider | `input.numeric.angle` | 输出一个可通过滑动条调节的角度值（度）。需要弧度时使用 Degrees To Radians。 | `AngleSliderNode` |
| Circular Angle Picker | `input.numeric.angle_picker` | 通过圆形表盘选择角度（度）。需要弧度时使用 Degrees To Radians。 | `CircularAngleNode` |
| XY Slider | `input.numeric.xy_slider` | Provides a two-dimensional slider pad that outputs X and Y values from one draggable handle | `XYSliderNode` |
| Domain Input | `input.numeric.range` | Defines a directed numeric domain (Start→End) and outputs domain, start, end, and directed span. | `RangeInputNode` |
| Pi | `input.numeric.pi` | Outputs the mathematical constant Pi. | `PiNode` |
| E | `input.numeric.e` | Outputs the mathematical constant e (approximately 2.718281828...). | `ENode` |

## input.type_selectors（4）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Block Type Selector | `input.type_selectors.block_type_selector` | Searches and selects a Minecraft block type. | `BlockTypeSelectorNode` |
| Entity Type Selector | `input.type_selectors.entity_type_selector` | Searches and selects a Minecraft entity type. | `EntityTypeSelectorNode` |
| Item Type Selector | `input.type_selectors.item_type_selector` | Searches and selects a Minecraft item type. | `ItemTypeSelectorNode` |
| Biome Selector | `input.type_selectors.biome_selector` | Selects a biome id for biome-aware generation workflows. | `BiomeSelectorNode` |

## input.values（6）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Text Input | `input.values.text_input` | Allows entering multi-line text. | `TextInputNode` |
| Color Picker | `input.values.color_picker` | Allows selecting a color value with RGB and alpha support. | `ColorPickerNode` |
| Boolean Toggle | `input.values.boolean_toggle` | Provides a boolean on/off toggle control. | `BooleanToggleNode` |
| Gradient Ramp | `input.values.gradient_ramp` | Creates and samples a customizable multi-stop gradient ramp with a visual editor | `GradientRampNode` |
| Value List | `input.values.dropdown` | Selects one value from a string option list and outputs index + text value. | `DropdownSelectorNode` |
| File Path Input | `input.values.file_path` | Selects or types a local file path and outputs it for file read/write nodes. | `FilePathInputNode` |

## material.basic_assignment（4）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Assign Block Type | `material.basic_assignment.assign_block_type` | Assigns a single block type to placements or geometry. Remaps blockId only; preserves stateData. | `AssignBlockTypeNode` |
| Create Block Palette | `material.basic_assignment.create_block_palette` | Builds a BLOCK_PALETTE from STRING_LIST block ids and optional DOUBLE_LIST weights | `CreateBlockPaletteNode` |
| Block Palette | `material.basic_assignment.block_palette` | Assigns palette block types cyclically. Flat: per-item; tree: per-branch. Remaps blockId only; preserves stateData. | `BlockPaletteNode` |
| Weighted Block Palette | `material.basic_assignment.weighted_palette` | Assigns weighted random block types by position + seed via RandomOps. Remaps blockId only; preserves stateData. | `WeightedBlockPaletteNode` |

## material.block_state（4）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Build Block State | `material.block_state.build_block_state` | Builds and validates block-state property data from a block type, base state, and overrides | `BuildBlockStateNode` |
| Orient Block State | `material.block_state.orient_block_state` | Derives facing, axis, and stair half block-state properties from a direction vector | `OrientBlockStateNode` |
| Apply Block State | `material.block_state.apply_block_state` | Merges block-state overrides into existing block placements | `ApplyBlockStateNode` |
| Stair Shape | `material.block_state.stair_shape` | Resolves stair corner shape from neighboring stair placements | `StairShapeNode` |

## material.directional_mapping（3）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Column Layer Map | `material.directional_mapping.top_side_bottom_map` | Column stratification map: highest / lowest / middle blocks per X/Z column. Remaps blockId only; preserves stateData. | `TopSideBottomMapNode` |
| Surface Slope Map | `material.directional_mapping.slope_map` | Surface material map: assigns flat/slope/steep by 4-neighbor column height grade on column-top voxels only. Remaps blockId only; preserves stateData. | `SlopeMapNode` |
| Slab / Stair Adapt | `material.directional_mapping.slab_stair_autofill` | Adapts block types to surface normals (blockId only). Use Orient Block State and Stair Shape for state properties. | `SlabStairAutofillNode` |

## material.gradient_mapping（5）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Height Gradient Map | `material.gradient_mapping.height_gradient_map` | Maps blocks by relative Y height into Bottom/Middle/Top/Peak bands. Remaps blockId only; preserves stateData. | `HeightGradientMapNode` |
| Noise Material | `material.gradient_mapping.noise_material` | Assigns block types across placements or geometry using deterministic 3D noise bands | `NoiseMaterialNode` |
| Height Palette Map | `material.gradient_mapping.gradient_ramp_map` | Assigns block types by height using equal bins from a BLOCK_PALETTE. Remaps blockId only; preserves stateData. | `GradientRampMapNode` |
| Distance-Based Material | `material.gradient_mapping.distance_material` | Assigns block types from a palette based on distance to exactly one reference point, plane, curve, polyline, or line. | `DistanceBasedMaterialNode` |
| SDF-Driven Material | `material.gradient_mapping.sdf_material` | Assigns block types from a palette using sampled SDF distance values. | `SdfDrivenMaterialNode` |

## material.pattern_mapping（4）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Checker Pattern Map | `material.pattern_mapping.checker_pattern_map` | Assigns alternating block types with a 3D checker (parity of relative X+Y+Z). Remaps blockId only; preserves stateData. | `CheckerPatternMapNode` |
| Stripe Pattern Map | `material.pattern_mapping.stripe_pattern_map` | Assigns alternating stripe materials along a selected axis relative to Pattern Origin. Remaps blockId only; preserves stateData. | `StripePatternMapNode` |
| Brick Pattern Map | `material.pattern_mapping.brick_pattern_map` | Assigns two materials using a staggered brick pattern relative to Pattern Origin. Remaps blockId only; preserves stateData. | `BrickPatternMapNode` |
| Grid Pattern Map | `material.pattern_mapping.grid_pattern_map` | Assigns frame/fill materials using an X/Z grid relative to Pattern Origin (Y extruded). Remaps blockId only; preserves stateData. | `GridPatternMapNode` |

## material.surface_aging（3）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Weathering | `material.surface_aging.weathering` | Ages exposed surface voxels with a deterministic RandomOps mask. Remaps blockId only; preserves stateData. | `WeatheringNode` |
| Moss Growth | `material.surface_aging.moss_growth` | Applies moss to upward-exposed (top-only) voxels with a deterministic RandomOps mask. Remaps blockId only; preserves stateData. | `MossGrowthNode` |
| Surface Cracks | `material.surface_aging.crack_pattern` | Applies sparse cracks to exposed surface voxels with a deterministic RandomOps mask. Remaps blockId only; preserves stateData. | `CrackPatternNode` |

## math.compare（6）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Equals (==) | `math.compare.equals` | Returns true when A equals B. | `EqualsNode` |
| Not Equals (!=) | `math.compare.not_equals` | Returns true when A does not equal B. | `NotEqualsNode` |
| Less Than (<) | `math.compare.less_than` | Returns true when A is less than B. | `LessThanNode` |
| Less Than or Equal (<=) | `math.compare.less_than_or_equal` | Returns true when A is less than or equal to B. | `LessThanOrEqualNode` |
| Greater Than (>) | `math.compare.greater_than` | Returns true when A is greater than B. | `GreaterThanNode` |
| Greater Than or Equal (>=) | `math.compare.greater_than_or_equal` | Returns true when A is greater than or equal to B. | `GreaterThanOrEqualNode` |

## math.data_tree（14）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Construct Tree Path | `math.data_tree.tree_path` | Builds a TREE_PATH from an ordered list of integer indices. | `ConstructTreePathNode` |
| Graft List | `math.data_tree.graft_list` | Converts each list item into its own data tree branch (preserves element type T). | `GraftListNode` |
| Flatten Tree | `math.data_tree.flatten` | Flattens all data tree branches into a single list (preserves element type T). | `FlattenTreeNode` |
| Partition List To Tree | `math.data_tree.partition_list` | Splits a list into fixed-size data tree branches (keeps incomplete last branch; preserves T). | `PartitionListToTreeNode` |
| Tree Branch | `math.data_tree.branch` | Gets one branch from a data tree by TREE_PATH (preserves T). Missing path → Found=false. | `TreeBranchNode` |
| Tree Item | `math.data_tree.item` | Gets one item from a data tree branch by TREE_PATH and index (negatives from end; OOR → Found=false). | `TreeItemNode` |
| Tree Statistics | `math.data_tree.statistics` | Reports branch count, item count, depth, and branch sizes for a data tree. | `TreeStatisticsNode` |
| Tree Viewer | `math.data_tree.viewer` | Outputs a readable summary of a data tree for debugging | `TreeViewerNode` |
| Merge Trees | `math.data_tree.merge` | Merges two data trees by concatenating items on matching paths (preserves T). | `MergeTreesNode` |
| Simplify Tree | `math.data_tree.simplify` | Removes the common leading path prefix from all data tree branches (preserves T). | `SimplifyTreeNode` |
| Shift Path | `math.data_tree.shift_path` | Moves data tree paths up by removing leading levels or down by adding zero levels. Colliding paths merge items. | `ShiftPathNode` |
| Tree Paths | `math.data_tree.paths` | Outputs all branch paths as a TREE_PATH_LIST. | `TreePathsNode` |
| Cull Empty Branches | `math.data_tree.cull_empty` | Removes empty branches from a data tree (preserves T). | `CullEmptyBranchesNode` |
| Entwine | `math.data_tree.entwine` | Combines up to four data trees under source-index path prefixes (preserves T). | `EntwineNode` |

## math.fields（17）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Scalar Field Constant | `math.fields.scalar_constant` | Builds a scalar field that returns a constant value everywhere. | `ScalarFieldConstantNode` |
| Scalar Field From SDF | `math.fields.scalar_from_sdf` | Wraps a signed distance field as a scalar field using its distance value. | `ScalarFieldFromSdfNode` |
| Scalar Field Noise | `math.fields.scalar_noise` | Builds a deterministic coherent noise scalar field over world space. | `ScalarFieldNoiseNode` |
| Combine Scalar Fields | `math.fields.scalar_binary_op` | Combines two scalar fields with a basic arithmetic operation. | `ScalarFieldBinaryOpNode` |
| Vector Field Constant | `math.fields.vector_constant` | Builds a vector field that returns a constant vector everywhere. | `VectorFieldConstantNode` |
| Vector Field From SDF Gradient | `math.fields.vector_from_sdf_gradient` | Builds a vector field from central-difference gradients of an SDF (normalized direction). | `VectorFieldFromSdfGradientNode` |
| Combine Vector Fields | `math.fields.vector_binary_op` | Combines two vector fields component-wise or via cross product. | `VectorFieldBinaryOpNode` |
| Point Attractor Field | `math.fields.point_attractor_field` | Builds a vector field that pulls points toward a center with configurable distance falloff. | `PointAttractorFieldNode` |
| Scalar Field Sample Point | `math.fields.scalar_sample_point` | Samples a scalar field at a point. | `ScalarFieldSamplePointNode` |
| Path Attractor Field | `math.fields.curve_attractor_field` | Builds a vector field that pulls points toward the closest point on a path. | `CurveAttractorFieldNode` |
| Scalar Field Sample Points | `math.fields.scalar_sample_points` | Samples a scalar field for each query point and outputs a value list. | `ScalarFieldSamplePointsNode` |
| Vector Field Sample Point | `math.fields.vector_sample_point` | Samples a vector field at a point. | `VectorFieldSamplePointNode` |
| Volume Attractor Field | `math.fields.volume_attractor_field` | Builds a volume-based attractor field using center-pull or nearest-surface pull from geometry/SDF inputs. | `VolumeAttractorFieldNode` |
| Vector Field Sample Points | `math.fields.vector_sample_points` | Samples a vector field for each query point and outputs a vector list. | `VectorFieldSamplePointsNode` |
| Vortex Field | `math.fields.vortex_field` | Builds a tangential swirl field around an axis, with radial falloff and clockwise/counter-clockwise control. | `VortexFieldNode` |
| Repulsor Field | `math.fields.repulsor_field` | Inverts a vector field direction (repulsion) with optional strength scaling. | `RepulsorFieldNode` |
| Blend Vector Fields | `math.fields.attractor_blend` | Blends up to four vector fields using per-field weights. | `AttractorFieldBlendNode` |

## math.list（23）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Create List | `math.list.create_list` | Packs multiple ANY items into a generic LIST. | `CreateListNode` |
| Deduplicate List | `math.list.deduplicate` | Removes duplicate values, keeping first occurrence order (preserves element type T). | `DeduplicateListNode` |
| List Statistics | `math.list.statistics` | Computes min, max, sum, average, and median for a DOUBLE_LIST. | `ListStatisticsNode` |
| Map Numbers | `math.list.map_numbers` | Applies a scalar operation to each value in a DOUBLE_LIST. | `MapListNode` |
| Sort Numbers | `math.list.sort_numbers` | Sorts a DOUBLE_LIST ascending or descending. | `SortNumbersNode` |
| Sort Text | `math.list.sort_text` | Sorts a STRING_LIST ascending or descending. | `SortTextNode` |
| Sum Numbers | `math.list.sum_numbers` | Sums a DOUBLE_LIST. | `SumNumbersNode` |
| Product Numbers | `math.list.product_numbers` | Multiplies all values in a DOUBLE_LIST. | `ProductNumbersNode` |
| Min Number | `math.list.min_number` | Minimum of a DOUBLE_LIST. | `MinNumberNode` |
| Max Number | `math.list.max_number` | Maximum of a DOUBLE_LIST. | `MaxNumberNode` |
| Average | `math.list.average` | Average of a DOUBLE_LIST. | `AverageNumbersNode` |
| Dispatch List | `math.list.dispatch_list` | Splits a list with a BOOLEAN_LIST mask of equal length (preserves element type T). | `DispatchListNode` |
| Filter List | `math.list.filter_list` | Filters a list with a BOOLEAN_LIST mask of equal length (preserves element type T). | `FilterListNode` |
| Flatten List | `math.list.flatten_list` | Flattens a nested list structure into a single-level list | `FlattenListNode` |
| Get Item | `math.list.get_item` | Gets an item from a list at a specified index. | `GetItemNode` |
| Group List | `math.list.group_list` | Groups list items by parallel keys into a DATA_TREE (one branch per unique key). | `GroupListNode` |
| Insert Item | `math.list.insert_item` | Inserts an item at index (negatives from end). Invalid index → Valid=false. | `InsertItemNode` |
| List Length | `math.list.list_length` | Returns the number of items in a list. | `ListLengthNode` |
| Remove Item | `math.list.remove_item` | Removes an item by index or value (preserves element type T). | `RemoveItemNode` |
| Reverse List | `math.list.reverse_list` | Reverses the order of elements in a list (preserves element type T). | `ReverseListNode` |
| Set Item | `math.list.set_item` | Sets an item at index (negatives from end). Invalid index → Success=false. | `SetItemNode` |
| Shuffle List | `math.list.shuffle_list` | Deterministically reorders a list using Seed (preserves element type T). | `ShuffleListNode` |
| Sub List | `math.list.sub_list` | Inclusive start / exclusive end slice. Negatives from end. Out-of-range → Valid=false. | `SubListNode` |

## math.logic（6）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| If | `math.logic.if` | Selects True Value when Condition is true; otherwise selects False Value. | `IfNode` |
| Switch | `math.logic.switch` | Selects one of multiple inputs by index, with a default fallback. | `SelectItemNode` |
| AND | `math.logic.and` | Returns true only when both boolean inputs are true. | `AndNode` |
| OR | `math.logic.or` | Returns true when either boolean input is true. | `OrNode` |
| NOT | `math.logic.not` | Returns the logical negation of the boolean input. | `NotNode` |
| XOR | `math.logic.xor` | Returns true when exactly one boolean input is true. | `XorNode` |

## math.random（6）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Random Number | `math.random.random_number` | Generates a single deterministic random double within a domain. | `RandomNumberNode` |
| Random Numbers | `math.random.random_numbers` | Generates a deterministic list of random doubles within a domain. | `RandomNumbersNode` |
| Random List Item | `math.random.random_list_item` | Deterministically selects one or more items from a list. | `RandomListItemNode` |
| Random Vector | `math.random.random_vector` | Generates a single deterministic random vector within a bounding box. | `RandomVectorNode` |
| Random Vectors | `math.random.random_vectors` | Generates a deterministic list of random vectors within a bounding box. | `RandomVectorsNode` |
| Noise | `math.random.noise` | Samples coherent 3D value noise from a position and seed. | `NoiseNode` |

## math.scalar_math（23）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Addition (+) | `math.scalar_math.addition` | Adds two numeric inputs. | `AdditionNode` |
| Subtraction (-) | `math.scalar_math.subtraction` | Outputs the result of A minus B. | `SubtractionNode` |
| Multiplication (*) | `math.scalar_math.multiplication` | Outputs the product of A and B. | `MultiplicationNode` |
| Division (/) | `math.scalar_math.division` | Outputs the result of A divided by B. | `DivisionNode` |
| Modulus (%) | `math.scalar_math.modulus` | Returns the remainder of A divided by B. | `ModulusNode` |
| Power (^) | `math.scalar_math.power` | Computes Base raised to Exponent. | `PowerNode` |
| Logarithm (log) | `math.scalar_math.logarithm` | Computes the logarithm of Number using Base. | `LogarithmNode` |
| Absolute (Abs) | `math.scalar_math.absolute` | Returns the absolute value of the input. | `AbsoluteNode` |
| Min | `math.scalar_math.min` | Returns the minimum of two values. | `MinNode` |
| Max | `math.scalar_math.max` | Returns the maximum of two values. | `MaxNode` |
| Clamp | `math.scalar_math.clamp` | Restricts a value to a domain's bounds (uses lower..upper, direction ignored). | `ClampNode` |
| Remap | `math.scalar_math.remap` | Maps a value from a source domain to a target domain. | `RemapNode` |
| Floor | `math.scalar_math.floor` | Rounds a value down to the nearest integer. | `FloorNode` |
| Ceiling | `math.scalar_math.ceiling` | Rounds a value up to the nearest integer. | `CeilingNode` |
| Round | `math.scalar_math.round` | Rounds a value to the nearest integer-valued double (ties-to-even). | `RoundNode` |
| Square Root | `math.scalar_math.sqrt` | Computes the square root of a numeric input. | `SqrtNode` |
| Lerp | `math.scalar_math.lerp` | Linearly interpolates between A and B using parameter T. | `LerpNode` |
| Integer Divide | `math.scalar_math.int_divide` | Performs floor-style integer division A / B and returns quotient and remainder. | `IntDivideNode` |
| Smoothstep | `math.scalar_math.smoothstep` | Computes smooth Hermite interpolation 3t^2 - 2t^3 between edge0 and edge1. | `SmoothstepNode` |
| Sign | `math.scalar_math.sign` | Returns -1, 0, or +1 based on the sign of the input value. | `SignNode` |
| Fraction (Frac) | `math.scalar_math.frac` | Returns the fractional part of x as x - floor(x). | `FracNode` |
| Graph Mapper | `math.scalar_math.graph_mapper` | Advanced: maps a value through a selectable normalized graph function (Grasshopper-style Graph Mapper) | `GraphMapperNode` |
| Expression | `math.scalar_math.expression` | Advanced: evaluates a numeric expression using variables A, B, C, X, Y, Z, and T | `ExpressionNode` |

## math.sequence（3）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Number Sequence | `math.sequence.range` | Generates a discrete DOUBLE_LIST from Start to End using Step. Not a continuous domain — use Domain Input for intervals. | `MathRangeNode` |
| Repeat Item | `math.sequence.repeat` | Repeats a single item Count times as a LIST. A list item is repeated as one element, never tiled. | `RepeatNode` |
| Number Series | `math.sequence.series` | Generates a DOUBLE_LIST with Start, Step, and Count (no Sum — use Sum Numbers). | `DataSeriesNode` |

## math.trigonometry（10）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Sine (Sin) | `math.trigonometry.sin` | Computes sine of an angle in degrees. | `SineNode` |
| Cosine (Cos) | `math.trigonometry.cos` | Computes cosine of an angle in degrees. | `CosineNode` |
| Tangent (Tan) | `math.trigonometry.tan` | Computes tangent of an angle in degrees. | `TangentNode` |
| Arcsine (ArcSin) | `math.trigonometry.asin` | Computes arcsine; result angle is in degrees. | `ArcSinNode` |
| Arccosine (ArcCos) | `math.trigonometry.acos` | Computes arccosine; result angle is in degrees. | `ArcCosNode` |
| Arctangent (ArcTan) | `math.trigonometry.atan` | Computes arctangent; result angle is in degrees. | `ArcTanNode` |
| Atan2 | `math.trigonometry.atan2` | Computes the signed angle in degrees from X and Y using atan2(Y, X). | `Atan2Node` |
| Sinh | `math.trigonometry.sinh` | Computes the hyperbolic sine of the input value. | `SinhNode` |
| Cosh | `math.trigonometry.cosh` | Computes the hyperbolic cosine of the input value. | `CoshNode` |
| Tanh | `math.trigonometry.tanh` | Computes the hyperbolic tangent of the input value. | `TanhNode` |

## math.vector（1）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Vector Component Min/Max | `math.vector.component_minmax` | Computes per-component min and max between vectors A and B. | `VectorComponentMinMaxNode` |

## output.debug（4）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Value Monitor | `output.debug.value_monitor` | 将任意输出连到输入，在面板上查看该输出的数据和类型 | `ValueMonitorNode` |
| Print To Chat | `output.debug.print_to_chat` | 将输入数据显示到游戏聊天框 | `PrintToChatNode` |
| Execution Timer | `output.debug.execution_timer` | 测量连接到此节点的计算分支所花费的时间 | `ExecutionTimerNode` |
| Panel | `output.debug.data_inspector` | 显示连接到其输入端口的原始数据（文本形式） | `PanelNode` |

## output.execute（8）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Apply Changes | `output.execute.apply_changes` | Submits explicit placements, placement trees, or voxelized geometry to the world. Async mode queues a single bake task and returns its task ID. | `ApplyChangesNode` |
| Bake Status | `output.execute.bake_status` | Polls BakePlacementService for a task ID and reports state, progress, placed, skipped, and rollback-failed counts. | `BakeStatusNode` |
| Clear Preview | `output.execute.clear_preview` | Clears all active previews | `ClearAllPreviewsNode` |
| Undo Last Bake | `output.execute.undo_last_bake` | Reverts the most recent recorded bake or apply-changes operation | `UndoLastBakeNode` |
| Bake Surface Strip To Blocks | `output.execute.bake_surface_strip_to_blocks` | Bakes a surface strip into block coordinates for final execution | `SurfaceStripToBlocksNode` |
| Redo Last Bake | `output.execute.redo_last_bake` | Reapplies the most recently undone bake or apply-changes operation | `RedoLastBakeNode` |
| SDF To Blocks | `output.execute.sdf_to_blocks` | Voxelizes a signed distance field directly into Minecraft block coordinates | `SdfToBlocksNode` |
| Merge Block Placements | `output.execute.merge_block_placements` | Merges block placement lists and placement trees into execution-ready placements | `MergeBlockPlacementsNode` |

## output.export（4）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Export Schematic | `output.export.export_schematic` | Exports placements to a NodeCraft NBT structure file | `ExportSchematicNode` |
| Export Litematic | `output.export.export_litematic` | Exports placements to a single-region Litematic file | `ExportLitematicNode` |
| Export WorldEdit | `output.export.export_worldedit` | Exports placements to a Sponge schematic file for WorldEdit | `ExportWorldEditNode` |
| Export CSV / JSON | `output.export.export_data` | Exports list/coordinates data to CSV or JSON file. | `ExportDataNode` |

## output.preview（12）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Geometry Viewer | `output.preview.geometry_viewer` | Previews geometry visually without committing changes to the world. | `GeometryViewerNode` |
| Preview Blocks | `output.preview.preview_blocks` | Previews block coordinates, placements, or placement trees as temporary ghost blocks. | `PreviewBlocksNode` |
| Preview Points | `output.preview.preview_points` | Previews one or more reference points before voxelization | `PreviewPointsNode` |
| Preview Vectors | `output.preview.preview_vectors` | Previews vectors and directions before voxelization | `PreviewVectorsNode` |
| Preview Plane | `output.preview.preview_plane` | Previews a plane as a square grid with axes and normal direction | `PreviewPlaneNode` |
| Preview Frame | `output.preview.preview_frame` | Previews a local coordinate frame with X, Y and Z axes | `PreviewFrameNode` |
| Preview Curves | `output.preview.preview_curves` | Previews lines, polylines and curves as reference paths | `PreviewPathsNode` |
| Preview Region | `output.preview.preview_regions` | Previews a region boundary as a reference box | `PreviewRegionsNode` |
| Preview Labels | `output.preview.preview_labels` | Displays a text label at a reference position | `PreviewLabelsNode` |
| Preview Surface Strip | `output.preview.preview_surface_strip` | Previews a surface strip as section contours, rails, or a lattice overlay | `PreviewSurfaceStripNode` |
| Preview Profiles | `output.preview.preview_profiles` | Previews polygon profile boundaries and optional normal indicators | `PreviewPolygonProfilesNode` |
| Preview Geometry | `output.preview.preview_geometry` | Previews analytic geometry as surfaces; voxel boolean (Difference/Intersection) as evaluated block ghosts matching bake | `PreviewGeometryNode` |

## pattern.grid（5）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Grid Array | `pattern.grid.grid_array` | Creates rectangular or box arrays of geometry using X, Y, and optional Z directions | `GridArrayNode` |
| Facade Grid | `pattern.grid.facade_grid` | Generates facade cell centers and boundaries on a box face | `FacadeGridNode` |
| Staggered Grid | `pattern.grid.staggered_grid` | Generates staggered grid anchor points with parity-controlled row offsets | `StaggeredGridNode` |
| Hex Grid | `pattern.grid.hex_grid` | Generates hexagonal lattice anchor points on the X/Z plane | `HexGridNode` |
| Triangular Grid | `pattern.grid.triangular_grid` | Generates triangular lattice anchor points with alternating row offsets | `TriangularGridNode` |

## pattern.linear（4）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Linear Array | `pattern.linear.linear_array` | Creates repeated geometry copies along a direction vector | `LinearArrayNode` |
| Path Frames | `pattern.linear.path_frames` | Generates parallel-transport frames at path vertices. | `PathFramesNode` |
| Instance on Points | `pattern.linear.instance_on_points` | Instances a block-placement template at each input point. | `InstanceOnPointsNode` |
| Curve Array | `pattern.linear.curve_array` | Creates repeated geometry copies along a curve using parallel-transport frames and placement | `CurveArrayNode` |

## pattern.lsystem（3）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| L-System Rule | `pattern.lsystem.rule` | Constructs one L-system production rule from symbol, production string, and relative weight | `LSystemRuleNode` |
| L-System Expand | `pattern.lsystem.expand` | Expands an L-system axiom using production rules for a fixed number of iterations (longest symbol match; weights are relative) | `LSystemExpandNode` |
| L-System Turtle 3D | `pattern.lsystem.turtle_3d` | Interprets L-system commands as independent 3D draw segments (PATH_LIST). F draws, f moves without drawing, +- yaw, &/^ pitch, / \ roll, [] stack | `LSystemTurtle3DNode` |

## pattern.radial（3）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Polar Array | `pattern.radial.polar_array` | Creates repeated geometry copies around a center point and axis | `PolarArrayNode` |
| Spiral | `pattern.radial.spiral` | Generates spiral anchor points with tangents and placement frames | `SpiralNode` |
| Phyllotaxis | `pattern.radial.phyllotaxis` | Generates golden-angle phyllotaxis anchor points with tangents and placement frames | `PhyllotaxisNode` |

## pattern.surface_volume_distribution（6）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Sample Sphere Surface | `pattern.surface_volume_distribution.sample_sphere_surface` | Samples points and normals on a sphere surface for scattering and growth workflows | `SampleSphereSurfaceNode` |
| Scatter On Surface | `pattern.surface_volume_distribution.scatter_surface` | Scatters points on supported primitive geometry surfaces with random or blue-noise distribution | `ScatterOnSurfaceNode` |
| Poisson Disk On Plane | `pattern.surface_volume_distribution.poisson_disk_plane` | Samples points on a plane inside a UV rectangle with minimum separation using rejection sampling | `PoissonDiskOnPlaneNode` |
| Scatter On Surface Strip | `pattern.surface_volume_distribution.scatter_surface_strip` | Scatters points on a surface strip by area-weighted quad sampling with optional spacing | `ScatterOnSurfaceStripNode` |
| Scatter In Volume | `pattern.surface_volume_distribution.scatter_volume` | Scatters points inside supported primitive geometry volumes with random or blue-noise distribution | `ScatterInVolumeNode` |
| Image Scatter | `pattern.surface_volume_distribution.image_scatter` | Scatters points using image density maps on a plane or world XZ | `ImageBasedScatterNode` |

## pattern.voronoi_3d（1）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Lloyd Relax 3D | `pattern.voronoi_3d.lloyd_relax` | Approximates Lloyd relaxation inside an axis-aligned 3D box using a uniform sampling grid. Not an exact Voronoi diagram. | `Voronoi3DLloydRelaxNode` |

## reference.frames（8）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Face Center Frame | `reference.frames.frame_from_face` | Builds an orthonormal frame at the center of a box face | `FaceCenterFrameNode` |
| Sphere Surface Frame | `reference.frames.sphere_surface_frame` | Builds a local tangent frame on a sphere at the projected surface point | `SphereSurfaceFrameNode` |
| World Frame | `reference.frames.world_frame` | Outputs the world coordinate frame as FRAME | `WorldFrameNode` |
| Construct Frame | `reference.frames.construct_frame` | Builds an orthonormal right-handed FRAME from origin, X axis, and Y axis (Z = X × Y) | `ConstructFrameNode` |
| Frame From Plane | `reference.frames.frame_from_plane` | Builds a right-handed orthonormal FRAME on a plane (Z = normal, X from hint) | `FrameFromPlaneNode` |
| Transform Frame | `reference.frames.transform_frame` | Applies translation and Euler rotation (degrees) to a FRAME. Output is orthonormal orientation-only. | `TransformFrameNode` |
| Deconstruct Frame | `reference.frames.deconstruct_frame` | Splits a FRAME into origin point, X/Y/Z axes, and plane | `DeconstructFrameNode` |
| Deconstruct Frames | `reference.frames.deconstruct_frames` | Splits a FRAME_LIST into origins, axes, and planes | `DeconstructFramesNode` |

## reference.planes（7）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| World Plane | `reference.planes.world_plane` | Creates a standard XY, YZ, or XZ world plane with a Point-compatible origin | `PlaneSelectorNode` |
| Construct Plane | `reference.planes.construct_plane` | Constructs a plane from an origin point and a normal vector | `ConstructPlaneNode` |
| Construct Plane From Points | `reference.planes.plane_from_points` | Constructs a plane from three non-collinear points | `ConstructPlaneFromPointsNode` |
| Box Face To Plane | `reference.planes.box_face_plane` | Converts a box face into its supporting plane | `BoxFaceToPlaneNode` |
| Offset Plane | `reference.planes.offset_plane` | Offsets a plane along its normal by a signed distance | `OffsetPlaneNode` |
| Distance Point To Plane | `reference.planes.distance_point_to_plane` | Measures the absolute and signed distance from a geometric point to a plane | `DistancePointToPlaneNode` |
| Deconstruct Plane | `reference.planes.deconstruct_plane` | Splits a PLANE into origin point and normal vector | `DeconstructPlaneNode` |

## reference.points（19）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Block Position Input | `reference.points.block_position` | Inputs an integer block position from panel values or optional X/Y/Z ports. | `CoordinateInputNode` |
| Construct Block Position | `reference.points.construct_coordinate` | Constructs a block position from X, Y, and Z integer components | `ConstructCoordinateNode` |
| Deconstruct Block Position | `reference.points.deconstruct_block_position` | Extracts X, Y, and Z integer components from a block position | `DeconstructCoordinateNode` |
| Block To Point | `reference.points.point_from_block` | Explicitly converts a block coordinate into a geometric point, with optional block-center offset | `BlockToPointNode` |
| Construct Point | `reference.points.construct_point` | Constructs a geometric point from X, Y, and Z double components | `ConstructPointNode` |
| Deconstruct Point | `reference.points.deconstruct_point` | Extracts X, Y, and Z double components from a geometric point | `DeconstructPointNode` |
| Translate Point | `reference.points.translate_point` | Translates a geometric point by a displacement vector (Point + Vector → Point) | `TranslatePointNode` |
| Move Point Along Direction | `reference.points.point_along_vector` | Moves a start point along a direction vector by a distance (direction is always normalized) | `PointAlongVectorNode` |
| Mid Point | `reference.points.mid_point` | Computes the midpoint between two input points | `MidpointNode` |
| Distance Between Points | `reference.points.distance_between_points` | Computes the distance between two input points | `DistanceNode` |
| Vector Between Points | `reference.points.vector_between_points` | Computes the displacement vector from one geometric point to another (To − From) | `VectorBetweenPointsNode` |
| Closest Point | `reference.points.closest_point` | Finds the closest geometric point in a point list to a reference point | `ClosestPointNode` |
| Point List Center | `reference.points.point_list_center` | Calculates the average geometric center of a point list | `PointListCenterNode` |
| Point List Bounds | `reference.points.point_list_bounds` | Calculates an axis-aligned bounding box from a list of geometric points | `PointListBoundsNode` |
| Get Box Corner | `reference.points.get_box_corner` | Gets a single corner from box geometry by index | `GetBoxCornerNode` |
| Get Box Face | `reference.points.get_box_face` | Gets a single face from box geometry by semantic name or index | `GetBoxFaceNode` |
| Get Face Edge | `reference.points.get_face_edge` | Gets a single edge from a face by index | `GetFaceEdgeNode` |
| Deconstruct Box Face | `reference.points.deconstruct_face` | Extracts corners, edges, plane, center, and normal from a box face | `DeconstructBoxFaceNode` |
| Deconstruct Face Edge | `reference.points.deconstruct_edge` | Extracts endpoints, midpoint, direction, displacement, and length from a face edge | `DeconstructFaceEdgeNode` |

## reference.vectors（17）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Vector Input | `reference.vectors.vector` | Inputs a 3D vector from panel values or optional X/Y/Z input ports | `VectorInputNode` |
| 2D Vector Input | `reference.vectors.vector2_input` | Inputs a 2D vector (X/Y) and outputs Vector3d(x, y, 0). | `Vector2InputNode` |
| Construct Vector | `reference.vectors.construct_vector` | Constructs a vector from X, Y, and Z components | `ConstructVectorNode` |
| Deconstruct Vector | `reference.vectors.deconstruct_vector` | Outputs the X, Y, and Z components of a vector. | `DeconstructVectorNode` |
| Vector Length | `reference.vectors.vector_length` | Computes the length (magnitude) of a vector. | `VectorLengthNode` |
| Normalize Vector | `reference.vectors.normalize_vector` | Normalizes a vector to unit length. | `NormalizeVectorNode` |
| Vector Addition (+) | `reference.vectors.vector_addition` | Computes the vector sum A + B. | `VectorAdditionNode` |
| Vector Subtraction (-) | `reference.vectors.vector_subtraction` | Computes the vector difference A - B. | `VectorSubtractionNode` |
| Vector Scalar Multiply | `reference.vectors.vector_scalar_multiply` | Multiplies a vector by a scalar. | `VectorScalarMultiplyNode` |
| Vector Scalar Divide | `reference.vectors.vector_scalar_divide` | Divides a vector by a scalar. | `VectorScalarDivideNode` |
| Dot Product | `reference.vectors.dot_product` | Computes the dot product of vectors A and B. | `DotProductNode` |
| Cross Product | `reference.vectors.cross_product` | Computes the cross product A x B and its magnitude. | `CrossProductNode` |
| Angle Between Vectors | `reference.vectors.angle_between` | Angle between two vectors in degrees; optional reference vector yields a signed angle | `AngleBetweenVectorsNode` |
| Lerp Vectors | `reference.vectors.lerp_vectors` | Linearly interpolates between vector A and B using parameter T. | `LerpVectorsNode` |
| Slerp Vectors | `reference.vectors.slerp` | Performs spherical linear interpolation between two direction vectors. | `SlerpVectorsNode` |
| Reflect Vector | `reference.vectors.reflect` | Reflects an input vector around a normal vector using v - 2(v·n)n. | `ReflectVectorNode` |
| Project Vector onto Vector | `reference.vectors.project` | Projects vector A onto vector B as (A·B / \|B\|^2)B. | `ProjectVectorNode` |

## transform.basic_transforms（9）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Move Geometry | `transform.basic_transforms.move_geometry` | Moves analytic geometry by a translation vector | `MoveGeometryNode` |
| Rotate Geometry Around Axis | `transform.basic_transforms.rotate_geometry_axis` | Rotates analytic geometry around a center point and arbitrary axis | `RotateGeometryAroundAxisNode` |
| Scale Geometry Around Point | `transform.basic_transforms.scale_geometry_point` | Uniformly scales analytic geometry around a center point (scale must be greater than zero; use Mirror for reflection) | `ScaleGeometryAroundPointNode` |
| Transform Geometry | `transform.basic_transforms.transform_geometry` | Applies translation, Euler XYZ rotation, and uniform scale to analytic geometry (primitives, composites, booleans, SDF wrappers) | `TransformGeometryNode` |
| Mirror Geometry About Plane | `transform.basic_transforms.mirror_geometry_plane` | Mirrors analytic geometry about a plane (recursive for composites and boolean geometry nodes) | `MirrorGeometryAboutPlaneNode` |
| Mirror Point List About Plane | `transform.basic_transforms.mirror_point_list_plane` | Mirrors each point in a POINT_LIST about a plane | `MirrorPointListAboutPlaneNode` |
| Transform Points by Frames | `transform.basic_transforms.transform_by_frames` | Transforms local POINT_LIST by FRAME_LIST into world-space positions (cartesian: Frame0 x all points, Frame1 x all points, ...). | `TransformPointsByFramesNode` |
| Offset Box Face | `transform.basic_transforms.offset_face` | Offsets a box face along its normal without modifying the source box geometry | `OffsetBoxFaceNode` |
| Inset Box Face | `transform.basic_transforms.inset_face` | Creates an inset or outset reference face boundary from a box face without modifying the source box | `InsetBoxFaceNode` |

## transform.deformations（11）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Twist Point List | `transform.deformations.twist` | Twists a point list around an axis by distributing rotation along a specified axial length | `TwistPointListNode` |
| Bend Point List | `transform.deformations.bend` | Bends a point list into an arc along an axis over a configurable bend length | `BendPointListNode` |
| Taper Point List | `transform.deformations.taper` | Scales radial distance along an axis to create tapered forms | `TaperPointListNode` |
| Shear Point List | `transform.deformations.shear_point_list` | Applies axial shear deformation to a point list around an origin. | `ShearPointListNode` |
| Noise Displace Point List | `transform.deformations.noise_displace` | Applies deterministic pseudo-noise displacement to a point list | `NoiseDisplacePointListNode` |
| Spherical Displace | `transform.deformations.spherical_displace` | Applies radial displacement with spherical distance falloff around a center point. | `SphericalDisplaceNode` |
| Path Attract Point List | `transform.deformations.curve_attract` | Pulls points toward a path with quadratic falloff; optional displacement along full vector, tangent only, or perpendicular-to-tangent only | `CurveAttractPointListNode` |
| Relax Point List | `transform.deformations.relax_points` | Laplacian-style smoothing using k nearest neighbors (uniform grid hash for speed) | `RelaxPointListNode` |
| Lattice Deform Point List | `transform.deformations.lattice_deform` | Free-form deformation: trilinear blend of control displacements on a uniform (nx+1)(ny+1)(nz+1) lattice in an axis-aligned box | `LatticeDeformPointListNode` |
| Twist Geometry | `transform.deformations.twist_geometry` | Applies an axial twist domain deformation to SDF or geometry, outputting a twisted SDF-backed Geometry | `TwistGeometryNode` |
| Bend Geometry | `transform.deformations.bend_geometry` | Applies an axial bend domain deformation to SDF or geometry before voxelization | `BendGeometryNode` |

## transform.orientation（6）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Project Point To Plane | `transform.orientation.project_to_plane` | Projects a geometric point onto a plane and reports the projection distance | `ProjectPointToPlaneNode` |
| Rotate Vector | `transform.orientation.rotate_vector` | Rotates a vector around an axis by an angle in degrees | `RotateVectorNode` |
| Align Points To Surface Normals | `transform.orientation.align_to_surface` | Builds oriented frames per point by aligning local up axis to surface normals. | `AlignPointsToSurfaceNormalsNode` |
| Project Points To Plane | `transform.orientation.project_points_to_plane` | Projects a list of points onto a target plane | `ProjectPointsToPlaneNode` |
| Project Curve To Plane | `transform.orientation.project_curve_to_plane` | Projects a curve, polyline, or line onto a target plane | `ProjectCurveToPlaneNode` |
| Project Profile To Plane | `transform.orientation.project_profile_to_plane` | Projects a polygon profile boundary onto a target plane | `ProjectProfileToPlaneNode` |

## transform.placement（8）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Place Geometry On Frames | `transform.placement.place_geometry_on_frames` | Places geometry copies onto FRAME / FRAME_LIST: pivot maps to each frame origin and local axes align to frame X/Y/Z | `PlaceGeometryOnFramesNode` |
| Place Geometry On Plane | `transform.placement.place_geometry_on_plane` | Places geometry onto a plane: builds a FRAME (Z=normal, X from hint) then maps pivot to plane origin | `PlaceGeometryOnPlaneNode` |
| Orient Geometry To Frame | `transform.placement.orient_geometry_to_frame` | Rotates geometry so local axes match FRAME X/Y/Z while keeping the pivot point fixed in world space | `OrientGeometryToFrameNode` |
| Offset Block Position | `transform.placement.offset_block_position` | Offsets a single block position by integer X, Y, Z amounts or a rounded vector | `OffsetBlockPositionNode` |
| Offset Block Positions | `transform.placement.offset_block_positions` | Offsets a list of block positions by a rounded vector | `OffsetBlockPositionsNode` |
| Rotate Block Positions | `transform.placement.rotate_block_positions` | Rotates a list of block positions around a point and axis | `RotateBlockPositionsNode` |
| Scale Block Positions | `transform.placement.scale_block_positions` | Scales a list of block positions relative to a center point | `ScaleBlockPositionsNode` |
| Mirror Block Positions | `transform.placement.mirror_block_positions` | Mirrors a block position list across a plane and snaps results to the block grid | `MirrorBlockPositionsNode` |

## utilities.assist（5）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| String Format | `utilities.assist.string_format` | Formats strings with placeholders like {0}, {1} from dynamic values. | `StringFormatNode` |
| Validate | `utilities.assist.validate` | Validates a boolean condition and gates a pass-through value. | `ValidateNode` |
| Coalesce | `utilities.assist.coalesce` | Returns the first non-null connected branch input by priority. | `CoalesceNode` |
| Relay | `utilities.assist.relay` | Passes a signal through, optionally with a visual semantic tag. | `RelayNode` |
| Signal Fork | `utilities.assist.signal_fork` | 将一路输入透传到两路输出，便于连线分流 | `SignalForkNode` |

## utilities.fileio（3）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Read Image | `utilities.fileio.read_image` | Reads a local image file into an IMAGE payload with metadata-first safety caps | `ReadImageNode` |
| Image Sampler | `utilities.fileio.image_sampler` | Samples color, channels, and grayscale values from IMAGE using UV or pixel coordinates | `ImageSamplerNode` |
| Import VOX | `utilities.fileio.import_vox` | Imports MagicaVoxel .vox structure as block coordinates, colors, and palette indices | `ImportVoxNode` |

## utilities.morphology（1）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Block List Morphology | `utilities.morphology.block_list_morphology` | Dilates or erodes a block list using 6- or 26-neighbor morphology iterations (Connectivity property) | `BlockListMorphologyNode` |

## utilities.organization（7）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Graph Input | `utilities.organization.graph_input` | Defines a named graph-level input with optional override and default fallback. | `GraphInputNode` |
| Graph Output | `utilities.organization.graph_output` | Defines a named graph-level output and publishes it into execution context. | `GraphOutputNode` |
| Subgraph | `utilities.organization.subgraph` | Executes a referenced subgraph with named input/output mapping. | `SubgraphNode` |
| Subgraph Register | `utilities.organization.subgraph_register` | Registers a subgraph reference into execution context for Subgraph calls. | `SubgraphRegisterNode` |
| Runtime Preset | `utilities.organization.preset` | Saves, loads, and deletes named runtime presets in execution context. | `NodePresetNode` |
| Comment | `utilities.organization.comment` | 在画布上添加文本注释 | `CommentNode` |
| Group | `utilities.organization.group` | 将选中的节点打包成一个可视化组 | `GroupNode` |

## variable（6）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Set Variable | `variable.set` | Stores a value under a user variable name in the execution scope. Connect an output to downstream nodes when write order matters. | `SetVariableNode` |
| Get Variable | `variable.get` | Reads a value by user variable name from the execution scope. Exists means the name exists, even when its stored value is null. | `GetVariableNode` |
| Variable List | `variable.list` | Lists user variables currently available in the execution scope. | `VariableListNode` |
| Frame Local Variable | `variable.frame_local` | Reads or writes variables in an isolated frame-local namespace. When Clear Frame and Write are both true, the frame is cleared first, then Value is written to Name. | `FrameLocalVariableNode` |
| Remove Variable | `variable.remove` | Removes a user variable from the execution scope. | `RemoveVariableNode` |
| Clear Variables | `variable.clear` | Clears user variables from the execution scope. | `ClearVariablesNode` |

## world.query（11）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Get Light Level | `world.query.get_light_level` | Gets the combined, sky, and block light values for a block position | `GetLightLevelNode` |
| Get Fluid Level | `world.query.get_fluid_level` | Gets the fluid state, type, and level for a block position | `GetFluidLevelNode` |
| Is Grid Point | `world.query.is_grid_point` | Checks whether a geometric point already lies on the block grid without snapping | `IsGridPointNode` |
| Filter Grid Points | `world.query.filter_grid_points` | Splits a point list into grid-aligned points and off-grid points without snapping | `FilterGridPointsNode` |
| Point In Region | `world.query.is_point_in_region` | Tests whether the center of a block position lies inside a region. | `IsPointInRegionNode` |
| Raycast | `world.query.raycast` | Casts a ray in world space and returns nearest block/entity hit information. | `RaycastNode` |
| Flood Fill | `world.query.flood_fill` | Runs BFS flood fill from a seed block using 6 or 26-neighbor connectivity. | `FloodFillNode` |
| Get Neighbor Blocks | `world.query.get_neighbors` | Returns axis-ray neighbors or cube-volume neighbors around a center position. | `GetNeighborBlocksNode` |
| Filter Points By Rule | `world.query.filter_points_by_rule` | Filters point sets by height and optional surface slope rules. | `FilterPointsByRuleNode` |
| Get Entities In Region | `world.query.get_entities_in_region` | Gets entities inside a region with optional filtering | `GetEntitiesInRegionNode` |
| Get Entity | `world.query.get_entity` | Finds an entity by UUID or by type near the current player. | `GetEntityNode` |

## world.read（12）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Get Block | `world.read.get_block` | Reads block state, type, light, fluid, and block-entity presence at a block position. | `GetBlockNode` |
| Get Blocks In Region | `world.read.get_blocks_in_region` | Reads block states and coordinates inside a region with scan limits. | `GetBlocksInRegionNode` |
| Find Blocks | `world.read.find_blocks` | Finds matching block positions inside a region using block type or exact block state matching. | `FindBlocksNode` |
| Get Biome | `world.read.get_biome` | Gets the biome registry id and basic climate data for a block position | `GetBiomeNode` |
| Biome At Player | `world.read.biome_at_player` | Gets the biome at the player's current position | `BiomeAtPlayerNode` |
| Get Points In Region | `world.read.get_points_in_region` | Generates or filters block positions inside a region with optional uniform sampling. | `GetPointsInRegionNode` |
| Get Heightmap | `world.read.get_heightmap` | Reads the top Y value for each X/Z column inside a region | `GetHeightmapNode` |
| Get Surface Blocks | `world.read.get_surface_blocks` | Gets the top visible block for each X/Z column inside a region | `GetSurfaceBlocksNode` |
| Scan Region By Type | `world.read.scan_region_by_type` | Scans a region and returns per-block-type counts for analysis and conditional building | `ScanRegionByTypeNode` |
| Get Block NBT | `world.read.get_block_nbt` | Reads full block-entity NBT data at a block position. | `GetBlockNbtNode` |
| Get Entity NBT | `world.read.get_entity_nbt` | Reads full entity NBT data from an entity object, UUID, or nearest type query. | `GetEntityNbtNode` |
| Read Sign Text | `world.read.read_sign_text` | Reads text from a sign block entity | `ReadSignTextNode` |

## world.selection（9）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Selected Block | `world.selection.selected_block` | 获取方块信息，支持交互拾取或坐标输入 | `SelectedBlockNode` |
| Selected Region | `world.selection.selected_region` | Gets the player's selected region defined by two corner points. | `SelectedRegionNode` |
| Snap Point To Block | `world.selection.snap_point_to_block` | Explicitly snaps a geometric point onto the block grid using floor, nearest, or ceil | `SnapPointToBlockNode` |
| Snap Vector To Block | `world.selection.snap_vector_to_block` | Converts a Vector3d position into a block coordinate using floor, round, or ceil snapping. | `SnapVectorToBlockNode` |
| Snap Point List To Blocks | `world.selection.snap_points_to_blocks` | Snaps a point list onto the block grid using an explicit snap mode | `SnapPointListToBlocksNode` |
| Point To Block If Grid | `world.selection.point_to_block_if_grid` | Strict conversion: outputs a block coordinate only when the point is already grid-aligned | `PointToBlockIfGridNode` |
| Selected Block Sequence | `world.selection.selected_block_sequence` | Collects multiple picked blocks in click order and outputs an ordered block sequence | `SelectedBlockSequenceNode` |
| Multi-Region Selection | `world.selection.multi_region` | Aggregates multiple non-contiguous region selections into a region list. | `MultiRegionSelectionNode` |
| Selected Entity | `world.selection.selected_entity` | Gets information about the entity selected by the player. | `SelectedEntityNode` |

## world.terrain（19）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Height Seed Field | `world.terrain.height_seed_field` | Builds a deterministic continental-scale seed height field over X/Z. | `HeightSeedFieldNode` |
| Plate Partition Field | `world.terrain.plate_partition_field` | Generates pseudo tectonic plate ids and boundary intensity via Voronoi-style partitioning. | `PlatePartitionFieldNode` |
| Orogenic Uplift Field | `world.terrain.orogenic_uplift_field` | Converts boundary intensity into mountain uplift potential. | `OrogenicUpliftFieldNode` |
| Rift Subsidence Field | `world.terrain.rift_subsidence_field` | Builds rift/trench subsidence strength from boundary intensity. | `RiftSubsidenceFieldNode` |
| Combine Height Fields | `world.terrain.combine_height_fields` | Combines base, additive, and subtractive height fields into one output field. | `CombineHeightFieldsNode` |
| Flow Direction Field | `world.terrain.flow_direction_field` | Computes downslope flow direction and slope magnitude from a height field. | `FlowDirectionFieldNode` |
| Flow Accumulation Field | `world.terrain.flow_accumulation_field` | Routes runoff with selectable fast or high-quality (MFD) flow accumulation. | `FlowAccumulationFieldNode` |
| River Mask Field | `world.terrain.river_mask_field` | Creates a river-channel mask field from flow accumulation. | `RiverMaskFieldNode` |
| Precipitation Field | `world.terrain.precipitation_field` | Builds precipitation from latitude bands and terrain rain-shadow response. | `PrecipitationFieldNode` |
| Thermal Erosion Step | `world.terrain.thermal_erosion_step` | Applies one thermal weathering step based on local slope exceeding talus angle. | `ThermalErosionStepNode` |
| Hydraulic Erosion Step | `world.terrain.hydraulic_erosion_step` | Applies one hydraulic erosion-deposition step with carrying capacity and optional flow-driven sediment transport. | `HydraulicErosionStepNode` |
| Deposition Step | `world.terrain.deposition_step` | Deposits sediment in low-slope and low-energy zones. | `DepositionStepNode` |
| Delta Accumulate Field | `world.terrain.delta_accumulate_field` | Applies or reverts delta fields and accumulates them into a combined terrain delta field. | `DeltaAccumulateFieldNode` |
| Temperature Field | `world.terrain.temperature_field` | Builds temperature from latitude bands and elevation lapse-rate cooling. | `TemperatureFieldNode` |
| Biome Classify | `world.terrain.biome_classify` | Classifies a biome index using temperature, precipitation, and elevation. | `BiomeClassifyNode` |
| Heightfield To Blocks | `world.terrain.heightfield_to_blocks` | Converts a height field inside a region to terrain block placements. | `HeightfieldToBlocksNode` |
| Biome Field To Blocks | `world.terrain.biome_field_to_blocks` | Maps biome id field to surface block placements using a configurable palette. | `BiomeFieldToBlocksNode` |
| Scalar Field Slice To Blocks | `world.terrain.scalar_field_slice_to_blocks` | Visualizes scalar field values on a horizontal slice using low/high block thresholds. | `ScalarFieldSliceToBlocksNode` |
| Sample Field On Region | `world.terrain.sample_field_on_region` | Samples a scalar field on a regular X/Z lattice inside a region. | `SampleFieldOnRegionNode` |

## world.write（18）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Set Block | `world.write.set_block` | Places one block at one block position, with optional block-entity NBT | `SetBlockNode` |
| Set Blocks | `world.write.set_blocks` | Sets blocks at explicit coordinates, with optional shared block-entity NBT | `SetBlocksNode` |
| Fill Region | `world.write.fill_region` | Fills a region with a block | `FillRegionNode` |
| Replace Blocks | `world.write.replace_blocks` | Replaces matching blocks in a region or coordinate list | `ReplaceBlocksNode` |
| Clone Region | `world.write.clone_region` | 复制区域到另一个位置 | `CloneRegionNode` |
| Clear Blocks | `world.write.remove_blocks` | Clears blocks at explicit coordinates by replacing them with air | `RemoveBlocksNode` |
| Set Block NBT | `world.write.set_block_nbt` | Writes or merges NBT data to a block entity at a target position. | `SetBlockNbtNode` |
| Undo Last World Write | `world.write.undo_last_write` | Reverts the most recent recorded world.write block placement operation | `UndoLastWorldWriteNode` |
| Peek Last World Write Undo | `world.write.peek_last_undo` | Inspects the latest world.write undo record and outputs affected count and region bounds | `PeekLastWorldWriteUndoNode` |
| Redo Last World Write | `world.write.redo_last_write` | Reapplies the most recently undone world.write block operation | `RedoLastWorldWriteNode` |
| Clear World Write Undo History | `world.write.clear_undo_history` | Clears all recorded world.write undo history entries | `ClearWorldWriteUndoHistoryNode` |
| Apply Redstone Power | `world.write.apply_redstone_power` | Places a temporary redstone power source next to a target block | `ApplyRedstonePowerNode` |
| Teleport Entity | `world.write.entity_teleport` | 传送实体 | `EntityTeleportNode` |
| Execute Command | `world.write.execute_command` | Executes a Minecraft command on the server | `ExecuteCommandNode` |
| Remove Entities | `world.write.remove_entities` | 移除实体 | `RemoveEntitiesNode` |
| Simulate Right Click | `world.write.simulate_right_click` | Simulates a server-side right click on a block | `SimulateRightClickNode` |
| Spawn Entity | `world.write.spawn_entity` | Spawns an entity into the world at a given position | `SpawnEntityNode` |
| Write Sign Text | `world.write.write_sign_text` | Writes text to a sign block entity | `WriteSignTextNode` |

## 文档生成说明

- 本文档由 `./gradlew generateNodeLibraryDocs` 从 `build/generated/nodeCatalog/node-catalog.json` 生成。
- 共享 SoT 与 `GeneratedNodeCatalog` 相同；请勿手改本文件。
- 需要更完整说明时，在对应节点的 `@NodeInfo.description` 中补充后重新生成。
