# NodeCraft Node Library

- Scope: `src/main/java/com/nodecraft/nodesystem/nodes`
- Total nodes: **521**
- Total categories: **54**

## Category Statistics

| Category ID | Node Count |
|---|---:|
| `flow.control` | 3 |
| `flow.loop` | 3 |
| `geometry.architectural_primitives` | 14 |
| `geometry.boolean` | 19 |
| `geometry.curves` | 24 |
| `geometry.primitives` | 29 |
| `geometry.profiles` | 24 |
| `geometry.solids` | 24 |
| `input.context` | 4 |
| `input.numeric` | 8 |
| `input.type_selectors` | 5 |
| `input.values` | 6 |
| `material.basic_assignment` | 3 |
| `material.block_state` | 8 |
| `material.directional_mapping` | 2 |
| `material.gradient_mapping` | 5 |
| `material.pattern_mapping` | 4 |
| `material.surface_aging` | 3 |
| `math.compare` | 7 |
| `math.data_tree` | 13 |
| `math.fields` | 17 |
| `math.list` | 22 |
| `math.logic` | 6 |
| `math.random` | 4 |
| `math.scalar_math` | 23 |
| `math.sequence` | 3 |
| `math.trigonometry` | 14 |
| `output.debug` | 4 |
| `output.execute` | 8 |
| `output.export` | 4 |
| `output.preview` | 12 |
| `pattern.grid` | 5 |
| `pattern.linear` | 7 |
| `pattern.lsystem` | 2 |
| `pattern.radial` | 4 |
| `pattern.surface_volume_distribution` | 9 |
| `pattern.voronoi_3d` | 1 |
| `reference.frames` | 4 |
| `reference.planes` | 6 |
| `reference.points` | 18 |
| `reference.vectors` | 18 |
| `transform.basic_transforms` | 15 |
| `transform.deformations` | 10 |
| `transform.orientation` | 6 |
| `utilities.assist` | 6 |
| `utilities.fileio` | 3 |
| `utilities.morphology` | 1 |
| `utilities.organization` | 7 |
| `variable` | 6 |
| `world.query` | 11 |
| `world.read` | 12 |
| `world.selection` | 9 |
| `world.terrain` | 19 |
| `world.write` | 18 |

## flow.control (3)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Branch | `flow.control.branch` | Routes an input signal to True or False output based on condition. | `BranchNode` |
| Sequence | `flow.control.sequence` | Replicates one signal to ordered sequence outputs. | `SequenceNode` |
| Do Once | `flow.control.do_once` | Allows a signal to pass once per execution context, unless reset. | `DoOnceNode` |

## flow.loop (3)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| For Each Loop | `flow.loop.for_each` | Iterates over a list and exposes derived iteration data. | `ForEachLoopNode` |
| Accumulator | `flow.loop.accumulator` | Accumulates list values into a single result. | `AccumulatorNode` |
| While Loop | `flow.loop.while` | Processes input values while condition remains true, with iteration safety limits. | `WhileLoopNode` |

## geometry.architectural_primitives (14)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Window Array | `geometry.architectural_primitives.window_array` | Generates a rectangular array of inset window opening boxes on a box face | `WindowArrayNode` |
| Door Array | `geometry.architectural_primitives.door_array` | Generates a rectangular array of inset door opening boxes on a box face | `DoorArrayNode` |
| Column Grid | `geometry.architectural_primitives.column_grid` | Generates a rectangular grid of columns on a box face | `ColumnGridNode` |
| Railing | `geometry.architectural_primitives.railing` | Generates a straight railing or balustrade along a line segment | `RailingNode` |
| Staircase | `geometry.architectural_primitives.staircase` | Generates architectural staircases from a line segment | `StaircaseNode` |
| Roof Generator | `geometry.architectural_primitives.roof_generator` | Generates configurable roof volumes from a box face footprint | `RoofGeneratorNode` |
| Facade Panel Array | `geometry.architectural_primitives.facade_panel_array` | Generates a rectangular array of facade panels on a box face | `FacadePanelArrayNode` |
| Arch Opening | `geometry.architectural_primitives.arch_opening` | Generates a rectangular, round, or pointed arch opening volume | `ArchOpeningNode` |
| Wall With Openings | `geometry.architectural_primitives.wall_with_openings` | Generates a wall slab with an opening grid | `WallWithOpeningsNode` |
| Pilaster / Cornice | `geometry.architectural_primitives.pilaster_cornice` | Generates pilasters and a cornice along a box face | `PilasterOrCorniceNode` |
| Array Along Curve | `geometry.architectural_primitives.array_along_curve` | Places repeated columns, posts, or panels along a curve or polyline path | `ArrayAlongCurveNode` |
| Deconstruct Architectural Opening | `geometry.architectural_primitives.deconstruct_opening` | Flattens architectural opening geometry into component lists and bounds | `DeconstructArchitecturalOpeningNode` |
| Floor Slab With Beams | `geometry.architectural_primitives.floor_slab_with_beams` | Generates a floor slab and a configurable support beam grid | `FloorSlabWithBeamsNode` |
| Molding Profile | `geometry.architectural_primitives.molding_profile` | Generates decorative molding cross-section profiles | `MoldingProfileNode` |

## geometry.boolean (19)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Bounding Box | `geometry.boolean.bounding_box` | Calculates an axis-aligned bounding box from a block list or region | `BoundingBoxNode` |
| Geometry Bounds | `geometry.boolean.geometry_bounds` | Calculates an axis-aligned bounding box from any supported geometry | `GeometryBoundsNode` |
| Combine Geometry | `geometry.boolean.union` | Merges multiple geometry inputs into one composite value (voxel union when baked; not SDF smooth union) | `GeometryUnionNode` |
| Difference | `geometry.boolean.difference` | Creates a voxel-evaluated difference geometry value by subtracting cutter geometry from a base geometry | `DifferenceNode` |
| Intersection | `geometry.boolean.intersection` | Creates a voxel-evaluated intersection geometry value from two geometry inputs | `IntersectionNode` |
| SDF Sphere | `geometry.boolean.sdf_sphere` | Builds a sphere signed-distance-field primitive from center and radius | `SdfSphereNode` |
| SDF Box | `geometry.boolean.sdf_box` | Builds an axis-aligned box signed-distance-field primitive from center and half extents | `SdfBoxNode` |
| SDF Capsule | `geometry.boolean.sdf_capsule` | Builds a capsule signed-distance-field primitive from segment endpoints and radius | `SdfCapsuleNode` |
| SDF Torus | `geometry.boolean.sdf_torus` | Builds a torus signed-distance-field primitive around the Y axis from center and radii | `SdfTorusNode` |
| SDF Boolean | `geometry.boolean.sdf_boolean` | Combines two SDF inputs with union/intersection/difference and optional smooth blending | `SdfBooleanNode` |
| SDF Smooth Boolean (Legacy) | `geometry.boolean.sdf_smooth_boolean` | Deprecated: use SDF Boolean with Smooth K > 0 for the same smooth union/intersection/difference | `SdfSmoothBooleanNode` |
| SDF To Geometry | `geometry.boolean.sdf_to_geometry` | Wraps an SDF into GeometryData with explicit or auto-estimated sampling bounds for block baking | `SdfToGeometryNode` |
| SDF Sample Point | `geometry.boolean.sdf_sample_point` | Samples signed distance at a query point and reports inside/outside state | `SdfSamplePointNode` |
| SDF Sample Points | `geometry.boolean.sdf_sample_points` | Samples signed distance for each query point and outputs distance and inside lists | `SdfSamplePointsNode` |
| SDF Gradient At Point | `geometry.boolean.sdf_gradient_point` | Samples SDF gradient at a point and outputs a normalized normal-like direction | `SdfGradientPointNode` |
| SDF Noise Displace | `geometry.boolean.sdf_noise_displace` | Applies deterministic pseudo-noise displacement to an input SDF | `SdfNoiseDisplaceNode` |
| SDF Transform | `geometry.boolean.sdf_transform` | Applies translation, rotation, and uniform scale to an input SDF | `SdfTransformNode` |
| SDF Blend Material Mask | `geometry.boolean.sdf_blend_material_mask` | Maps SDF distance values to smooth 0..1 blend weights and inside/outside booleans | `SdfBlendMaterialMaskNode` |
| SDF Domain Warp | `geometry.boolean.sdf_domain_warp` | Applies coordinate-space noise warping before sampling an input SDF | `SdfDomainWarpNode` |

## geometry.curves (24)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Points To Path | `geometry.curves.curve_from_points` | Builds a line or polyline from an ordered point list | `PointsToPathNode` |
| Path To Points | `geometry.curves.divide_curve_to_points` | Extracts an ordered point list from a line, polyline, or curve | `PathToPointsNode` |
| Arc | `geometry.curves.arc` | Builds a sampled circular arc from a center point, plane, radius, and start/end angles | `ArcNode` |
| Face Edge To Path | `geometry.curves.edge_to_curve` | Converts a face edge into line, polyline, and point outputs for path workflows | `FaceEdgeToPathNode` |
| Bezier | `geometry.curves.bezier` | Builds a sampled Bezier curve from an ordered list of control points | `BezierNode` |
| Box Face Boundary Path | `geometry.curves.face_boundary_curve` | Builds a closed boundary path from a box face for preview and downstream path workflows | `BoxFaceBoundaryPathNode` |
| Interpolate Spline | `geometry.curves.interpolate_spline` | Builds a Catmull-Rom interpolation spline that passes through all resolved input points | `InterpolateSplineNode` |
| Offset Polyline In Plane | `geometry.curves.offset_polyline_plane` | Offsets a polyline in a plane using parallel segments and miters (left is CCW in the plane UV basis) | `PolylineOffsetInPlaneNode` |
| B-Spline | `geometry.curves.b_spline` | Builds a sampled clamped uniform B-spline from an ordered control point list | `BSplineNode` |
| Fillet Polyline Corners | `geometry.curves.fillet_polyline_corners` | Fillets interior corners of an open polyline with circular arcs in the work plane | `PolylineCornerFilletNode` |
| Offset Curve In Plane | `geometry.curves.offset_curve_plane` | Offsets a curve, polyline, or line in a work plane by signed distance. | `OffsetCurveInPlaneNode` |
| NURBS Curve | `geometry.curves.nurbs` | Builds a sampled clamped uniform NURBS curve from control points and optional per-point weights | `NurbsCurveNode` |
| Rainbow Curve Offset | `geometry.curves.rainbow_curve_offset` | Generates multiple parallel offset polylines around a space curve using path frames. | `RainbowCurveOffsetNode` |
| Resample Polyline By Length | `geometry.curves.resample_polyline_length` | Resamples a polyline along its arc length using spacing, or using a total point count (count wins when both are provided) | `ResamplePolylineByLengthNode` |
| Curve Rebuild By Length | `geometry.curves.rebuild_curve_length` | Rebuilds a curve/path to uniform arc-length samples using spacing, or using a total point count (count wins when both are provided) | `CurveRebuildByLengthNode` |
| Polyline Length | `geometry.curves.polyline_length` | Computes the total length of a polyline or line segment | `PolylineLengthNode` |
| Curve Evaluate | `geometry.curves.evaluate_curve` | Evaluates a curve/path at normalized parameter t and outputs point, tangent, normal, and binormal | `CurveEvaluateNode` |
| Curve Frame Along Path | `geometry.curves.frame_along_path` | Generates local frames along a curve/path using count or spacing, outputting origins, axes, and planes per sample | `CurveFrameAlongPathNode` |
| Parabola On Plane | `geometry.curves.parabola_curve` | Builds a sampled parabola on a plane from vertex, curvature, x-range, and segment count | `ParabolaOnPlaneNode` |
| Helix Curve | `geometry.curves.helix` | Builds a sampled helix from center, axis, radius, pitch, turns, and segment count. | `HelixCurveNode` |
| Infinity Curve On Plane | `geometry.curves.infinity_curve` | Builds a sampled figure-eight (lemniscate-like) curve on a plane | `InfinityCurveOnPlaneNode` |
| Voxelize Curve | `geometry.curves.voxelize_curve` | Converts a curve, polyline, or line directly into voxel block coordinates using cylindrical path segments | `VoxelizeCurveNode` |
| Blend Curves | `geometry.curves.blend_curves` | Creates a smooth transition curve between two curve, polyline, or line endpoints | `BlendCurvesNode` |
| Tween Curves | `geometry.curves.tween_curves` | Creates evenly spaced intermediate curves between two curve, polyline, or line inputs | `TweenCurvesNode` |

## geometry.primitives (29)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Box by Center + Size | `geometry.primitives.box` | Generates a box from a center point and explicit X/Y/Z sizes | `BoxCenterSizeNode` |
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

## geometry.profiles (24)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Rectangle On Plane | `geometry.profiles.rectangle_profile` | Constructs a planar rectangle from a center point, width, height, and plane | `RectangleOnPlaneNode` |
| Regular Polygon On Plane | `geometry.profiles.polygon_profile` | Constructs a regular polygon from a center point, radius, side count, and plane | `RegularPolygonOnPlaneNode` |
| Polygon By Points | `geometry.profiles.custom_profile` | Constructs a planar polygon profile from an ordered point list | `PolygonByPointsNode` |
| Resample Polygon Profile | `geometry.profiles.resample_profile` | Resamples a polygon profile to a target edge count using perimeter-distance sampling | `ResamplePolygonProfileNode` |
| Deconstruct Polygon Profile | `geometry.profiles.deconstruct_profile` | Extracts points, boundary, plane, center, perimeter, and area from a polygon profile | `DeconstructPolygonProfileNode` |
| Convex Hull 2D On Plane | `geometry.profiles.convex_hull_plane` | Projects points into a plane, computes their 2D convex hull, and outputs a closed polygon profile | `ConvexHull2DOnPlaneNode` |
| Voronoi Cells 2D On Plane | `geometry.profiles.voronoi_cells_plane` | Projects sites into a plane, builds a clipped planar Voronoi diagram (JTS), and outputs each cell as a polygon profile on the plane | `VoronoiCells2DOnPlaneNode` |
| Convex Hull 3D From Points | `geometry.profiles.convex_hull_3d_points` | Builds a 3D convex hull (triangle facets) from points; intended for small clouds due to brute-force enumeration; coplanar / collinear inputs yield no facets | `ConvexHull3DFromPointsNode` |
| Circle On Plane | `geometry.profiles.circle_profile` | Constructs a circular profile from center, radius, plane, and segment count | `CircleOnPlaneNode` |
| Ellipse On Plane | `geometry.profiles.ellipse_profile` | Constructs an ellipse profile from center, major/minor radii, plane, and segment count | `EllipseOnPlaneNode` |
| Sector On Plane | `geometry.profiles.sector_profile` | Constructs a circular sector profile from center, radius, start/end angles, and plane | `SectorOnPlaneNode` |
| Annulus On Plane | `geometry.profiles.annulus_profile` | Constructs annulus boundaries from center, inner/outer radii, plane, and segment count | `AnnulusOnPlaneNode` |
| Rounded Rectangle On Plane | `geometry.profiles.rounded_rectangle_profile` | Constructs a rounded-rectangle profile from center, width, height, corner radius, and plane | `RoundedRectangleOnPlaneNode` |
| Star Polygon On Plane | `geometry.profiles.star_polygon_profile` | Constructs a star polygon profile from center, inner/outer radii, point count, and plane | `StarPolygonOnPlaneNode` |
| SemiCircle On Plane | `geometry.profiles.semicircle_profile` | Constructs a semicircle profile from center, radius, plane, and segment count | `SemiCircleOnPlaneNode` |
| Rhombus On Plane | `geometry.profiles.rhombus_profile` | Constructs a rhombus profile from center, horizontal diagonal, vertical diagonal, and plane | `RhombusOnPlaneNode` |
| Capsule On Plane | `geometry.profiles.capsule_profile` | Constructs a capsule (stadium) profile from center, length, radius, and plane | `CapsuleOnPlaneNode` |
| Heart On Plane | `geometry.profiles.heart_profile` | Constructs a heart profile from center, width, height, plane, and segment count | `HeartOnPlaneNode` |
| Annular Sector On Plane | `geometry.profiles.annular_sector_profile` | Constructs an annular sector boundary from center, inner/outer radii, angle range, and plane | `AnnularSectorOnPlaneNode` |
| Cross On Plane | `geometry.profiles.cross_profile` | Constructs a plus-shaped cross profile from arm length, arm width, center, and plane | `CrossOnPlaneNode` |
| Gear On Plane | `geometry.profiles.gear_profile` | Constructs a gear-like profile from center, tooth count, root/tip radii, and plane | `GearOnPlaneNode` |
| Profile Offset In Plane | `geometry.profiles.offset_profile_plane` | Offsets a polygon profile in its plane by signed distance using 2D buffer logic | `ProfileOffsetInPlaneNode` |
| Profile Boolean 2D | `geometry.profiles.boolean_2d` | Performs 2D boolean operations (union/intersection/difference) on two polygon profiles in a shared plane | `ProfileBoolean2DNode` |
| Profile Triangulate 2D | `geometry.profiles.triangulate_2d` | Triangulates a planar polygon profile into triangle profiles using ear clipping | `ProfileTriangulate2DNode` |

## geometry.solids (24)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Extrude Profile | `geometry.solids.extrude` | Extrudes a polygon profile by a direction vector and emits prism geometry plus side surface strip data | `ExtrudeProfileNode` |
| Extrude Point List | `geometry.solids.extrude_from_points` | Extrudes an ordered point list by a direction vector and emits source path, top path, and side segments | `ExtrudePointListNode` |
| Extrude Box Face | `geometry.solids.extrude_box_face` | Extrudes a box face into a new box segment and returns a composite geometry | `ExtrudeBoxFaceNode` |
| Loft Profiles | `geometry.solids.loft` | Lofts two polygon profiles with matching edge counts into a side surface strip | `LoftProfilesNode` |
| Loft Point Lists | `geometry.solids.loft_from_points` | Connects two ordered point lists and emits source paths, target paths, and loft rail segments | `LoftPointListsNode` |
| Sweep Profile Along Path | `geometry.solids.sweep` | Sweeps a polygon profile along a path with optional scale, rotation, close, and flip controls | `SweepProfileAlongPathNode` |
| Sweep Point List Along Path | `geometry.solids.sweep_from_points` | Sweeps an ordered point profile along a path with optional scale, rotation, close, and flip controls | `SweepPointListAlongPathNode` |
| Revolve Profile | `geometry.solids.revolve` | Revolves a polygon profile around an axis and emits section profiles plus a side surface strip | `RevolveProfileNode` |
| Surface Strip To Geometry | `geometry.solids.surface_strip_to_geometry` | Explicitly converts a surface strip into reusable geometry by sampling section edges and rails as cylinders | `SurfaceStripToGeometryNode` |
| Sweep 2 Rails | `geometry.solids.sweep_two_rails` | Sweeps a profile between two guide rails with optional scale and rotation controls | `SweepTwoRailsNode` |
| Push/Pull Box Face | `geometry.solids.push_pull_face` | Moves one box face along its normal and outputs a new box geometry | `PushPullBoxFaceNode` |
| Shell Surface Strip | `geometry.solids.shell` | Builds inner and outer offset shell layers from a surface strip and emits cap strips plus an optional geometry approximation | `ShellNode` |
| Prism By Profile Vector | `geometry.solids.extrude_profile` | Constructs prism geometry from a polygon profile and an extrusion vector | `PrismByProfileVectorNode` |
| Thicken Surface | `geometry.solids.thicken_surface` | Thickens a surface strip into two offset layers with optional cap strips and a reusable geometry approximation | `ThickenSurfaceNode` |
| Contour | `geometry.solids.contour` | Generates parallel section planes and traces voxel contour profiles from geometry at regular spacing. | `ContourNode` |
| Prism By Base Points Vector | `geometry.solids.extrude_profile_from_points` | Constructs prism geometry from an ordered base polygon and an extrusion vector | `PrismByBasePointsVectorNode` |
| Section Cut | `geometry.solids.section_cut` | Cuts geometry by one or more planes and traces voxel slice contours as section profiles, boundaries, blocks, and tree-grouped data. | `SectionCutNode` |
| Deconstruct Surface Strip | `geometry.solids.deconstruct_surface_strip` | Breaks a surface strip into section paths, flattened points, and rail segments | `DeconstructSurfaceStripNode` |
| Multi-Section Loft | `geometry.solids.loft_multi_section` | Lofts multiple polygon sections into one surface strip with close, flip, seam, and resample options. | `MultiSectionLoftNode` |
| Extract Surface Strip Range | `geometry.solids.extract_surface_strip_range` | Extracts a contiguous section range from a surface strip as a smaller surface strip | `ExtractSurfaceStripRangeNode` |
| Morph Between Profiles | `geometry.solids.morph_profiles` | Interpolates between two compatible polygon profiles using parameter t in [0,1]. | `MorphBetweenProfilesNode` |
| Offset Surface Strip | `geometry.solids.offset_surface_strip` | Offsets a surface strip by a signed distance and outputs a single offset surface | `OffsetSurfaceStripNode` |
| Shrinkwrap Points On Surface Strip | `geometry.solids.shrinkwrap_points_surface_strip` | Projects each query point to the closest location on the surface strip triangle mesh | `ShrinkwrapPointsOnSurfaceStripNode` |
| Shrinkwrap Points To Voxel Geometry | `geometry.solids.shrinkwrap_points_voxel_geometry` | Voxelizes geometry to blocks, then snaps each query point to the nearest voxel block center (shell when fill is off); distinct from triangle strip shrinkwrap | `ShrinkwrapPointsToVoxelGeometryNode` |

## input.context (4)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Player Position | `input.context.player_position` | Gets the player's current world position. | `PlayerPositionNode` |
| Player Look At | `input.context.player_look_direction` | Gets the player's look direction and current raycast hit information. | `PlayerLookAtNode` |
| Dimension Info | `input.context.dimension_info` | Gets the current dimension and basic dimension traits from the active Minecraft world. | `DimensionInfoNode` |
| Current Time | `input.context.current_time` | Gets the current time and weather state from the active Minecraft world. | `CurrentTimeNode` |

## input.numeric (8)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Integer Input | `input.numeric.integer` | 允许手动输入整数值的节点 | `IntegerInputNode` |
| Float Input | `input.numeric.float` | 允许用户手动输入浮点数值 | `FloatInputNode` |
| Integer Slider | `input.numeric.integer_slider` | 输出一个可通过滑动条调节的整数值 | `IntegerSliderNode` |
| Float Slider | `input.numeric.float_slider` | 输出一个可通过滑动条调节的浮点数。 | `FloatSliderNode` |
| Angle Slider | `input.numeric.angle` | 输出一个可通过滑动条调节的角度值，支持度和弧度输出。 | `AngleSliderNode` |
| Circular Angle Picker | `input.numeric.angle_picker` | 通过圆形表盘选择角度，同时输出度和弧度。 | `CircularAngleNode` |
| XY Slider | `input.numeric.xy_slider` | Provides a two-dimensional slider pad that outputs X and Y values from one draggable handle | `XYSliderNode` |
| Range Input | `input.numeric.range` | Defines a numeric interval and outputs min/max/span plus a range object. | `RangeInputNode` |

## input.type_selectors (5)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Block Type Selector | `input.type_selectors.block_type_selector` | Searches and selects a Minecraft block type. | `BlockTypeSelectorNode` |
| Entity Type Selector | `input.type_selectors.entity_type_selector` | Searches and selects a Minecraft entity type. | `EntityTypeSelectorNode` |
| Item Type Selector | `input.type_selectors.item_type_selector` | Searches and selects a Minecraft item type. | `ItemTypeSelectorNode` |
| Biome Selector | `input.type_selectors.biome_selector` | Selects a biome id for biome-aware generation workflows. | `BiomeSelectorNode` |
| Block State Selector | `input.type_selectors.block_state_selector` | Builds block-state key/value data from a compact properties string. | `BlockStateSelectorNode` |

## input.values (6)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Text Input | `input.basic.text_input` | Allows entering single-line or multi-line text. | `TextInputNode` |
| Color Picker | `input.basic.color_picker` | Allows selecting a color value with RGB and alpha support. | `ColorPickerNode` |
| Boolean Toggle | `input.basic.boolean_toggle` | 提供一个可以切换的布尔值开关控制 | `BooleanToggleNode` |
| Gradient Ramp | `input.values.gradient_ramp` | Creates and samples a customizable multi-stop gradient ramp with a visual editor | `GradientRampNode` |
| Dropdown Selector | `input.values.dropdown` | Selects one value from user-defined option list and outputs index + text value. | `DropdownSelectorNode` |
| File Path Input | `input.values.file_path` | Selects or types a local file path and outputs it for file read/write nodes. | `FilePathInputNode` |

## material.basic_assignment (3)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Assign Block Type | `material.basic_assignment.assign_block_type` | Assigns a single block type to flat or tree-grouped block positions | `AssignBlockTypeNode` |
| Block Palette | `material.basic_assignment.block_palette` | Assigns palette block types to flat positions or tree branches | `BlockPaletteNode` |
| Weighted Block Palette | `material.basic_assignment.weighted_palette` | Assigns weighted random block types to flat positions or tree branches. | `WeightedBlockPaletteNode` |

## material.block_state (8)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Block State Assign | `material.block_state.block_state_assign` | Assigns explicit block-state properties to placements or voxelized geometry | `BlockStateAssignNode` |
| Auto Orient Blocks | `material.block_state.auto_orient_blocks` | Automatically assigns a facing state from the dominant direction axis | `AutoOrientBlocksNode` |
| Stair Shape | `material.block_state.stair_shape` | Assigns stair facing, half, and inner/outer corner shape states | `StairShapeNode` |
| Waterlogged State | `material.block_state.waterlogged` | Assigns the waterlogged block-state property to placements or voxelized geometry. | `WaterloggedStateNode` |
| Facing From Normal | `material.block_state.facing_from_normal` | Converts normal vectors to nearest facing direction and optionally writes facing state to placements. | `FacingFromNormalNode` |
| Slab / Stair Auto-Fill | `material.block_state.slab_autofill` | Generates slab or stair placements from normals to smooth stepped transitions. | `SlabStairAutofillNode` |
| Build BlockState | `material.block_state.build_block_state` | Builds block-state key/value data from a block id, base state, and dynamic property override | `BuildBlockStateNode` |
| Orient BlockState | `material.block_state.orient_block_state` | Derives facing, axis, and stair half block-state properties from a normal or tangent vector | `OrientBlockStateNode` |

## material.directional_mapping (2)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Top / Side / Bottom Map | `material.directional_mapping.top_side_bottom_map` | Assigns top, side, and bottom block types across a voxelized shape using vertical exposure | `TopSideBottomMapNode` |
| Slope Map | `material.directional_mapping.slope_map` | Assigns flat/slope/steep materials using local height difference per X/Z column. | `SlopeMapNode` |

## material.gradient_mapping (5)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Height Gradient Map | `material.gradient_mapping.height_gradient_map` | Assigns lower, middle, and upper block types across a shape based on relative height | `HeightGradientMapNode` |
| Noise Material | `material.gradient_mapping.noise_material` | Assigns block types across placements or geometry using deterministic 3D noise bands | `NoiseMaterialNode` |
| Gradient Ramp Map | `material.gradient_mapping.gradient_ramp_map` | Assigns block types by height using a custom multi-stop ramp list. | `GradientRampMapNode` |
| Distance-Based Material | `material.gradient_mapping.distance_material` | Assigns block types from a palette based on distance to a reference point, plane, curve, or line. | `DistanceBasedMaterialNode` |
| SDF-Driven Material | `material.gradient_mapping.sdf_material` | Assigns block types from a palette using sampled SDF distance values. | `SdfDrivenMaterialNode` |

## material.pattern_mapping (4)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Checker Pattern Map | `material.pattern_mapping.checker_pattern_map` | Assigns alternating block types across a voxelized shape using a checker pattern | `CheckerPatternMapNode` |
| Stripe Pattern Map | `material.pattern_mapping.stripe_pattern_map` | Assigns alternating stripe materials along a selected axis. | `StripePatternMapNode` |
| Brick Pattern Map | `material.pattern_mapping.brick_pattern_map` | Assigns two materials using a staggered brick-like pattern in X/Z. | `BrickPatternMapNode` |
| Grid Pattern Map | `material.pattern_mapping.grid_pattern_map` | Assigns frame/fill materials using a regular X/Z grid interval. | `GridPatternMapNode` |

## material.surface_aging (3)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Weathering | `material.surface_aging.weathering` | Replaces part of a block set with an aged material using a deterministic weathering ratio | `WeatheringNode` |
| Moss Growth | `material.surface_aging.moss_growth` | Applies moss material preferentially to upward-facing/exposed blocks. | `MossGrowthNode` |
| Crack Pattern | `material.surface_aging.crack_pattern` | Adds deterministic crack lines by replacing sparse diagonal bands. | `CrackPatternNode` |

## math.compare (7)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Compare | `math.compare.compare` | Compares two values and outputs equality and ordering relations. | `CompareNode` |
| Equals (==) | `math.compare.equals` | Returns true when A equals B. | `EqualsNode` |
| Not Equals (!=) | `math.compare.not_equals` | Returns true when A does not equal B. | `NotEqualsNode` |
| Less Than (<) | `math.compare.less_than` | Returns true when A is less than B. | `LessThanNode` |
| Less Than or Equal (<=) | `math.compare.less_than_or_equal` | Returns true when A is less than or equal to B. | `LessThanOrEqualNode` |
| Greater Than (>) | `math.compare.greater_than` | Returns true when A is greater than B. | `GreaterThanNode` |
| Greater Than or Equal (>=) | `math.compare.greater_than_or_equal` | Returns true when A is greater than or equal to B. | `GreaterThanOrEqualNode` |

## math.data_tree (13)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Graft List | `math.data_tree.graft_list` | Converts each list item into its own data tree branch | `GraftListNode` |
| Flatten Tree | `math.data_tree.flatten` | Flattens all data tree branches into a single list | `FlattenTreeNode` |
| Partition List To Tree | `math.data_tree.partition_list` | Splits a list into fixed-size data tree branches | `PartitionListToTreeNode` |
| Tree Branch | `math.data_tree.branch` | Gets one branch from a data tree by path | `TreeBranchNode` |
| Tree Item | `math.data_tree.item` | Gets one item from a data tree branch by path and index | `TreeItemNode` |
| Tree Statistics | `math.data_tree.statistics` | Reports branch count, item count, depth, paths, and branch sizes for a data tree | `TreeStatisticsNode` |
| Tree Viewer | `math.data_tree.viewer` | Outputs a readable summary of a data tree for debugging | `TreeViewerNode` |
| Merge Trees | `math.data_tree.merge` | Merges two data trees or lists into one data tree | `MergeTreesNode` |
| Simplify Tree | `math.data_tree.simplify` | Removes the common leading path prefix from all data tree branches | `SimplifyTreeNode` |
| Shift Path | `math.data_tree.shift_path` | Moves data tree paths up by removing leading levels or down by adding zero levels | `ShiftPathNode` |
| Tree Paths | `math.data_tree.paths` | Outputs data tree branch paths as strings and path index lists | `TreePathsNode` |
| Cull Empty Branches | `math.data_tree.cull_empty` | Removes empty branches from a data tree | `CullEmptyBranchesNode` |
| Entwine | `math.data_tree.entwine` | Combines up to four trees or lists into source-indexed data tree branches | `EntwineNode` |

## math.fields (17)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Scalar Field Constant | `math.fields.scalar_constant` | Builds a scalar field that returns a constant value everywhere. | `ScalarFieldConstantNode` |
| Scalar Field From SDF | `math.fields.scalar_from_sdf` | Wraps a signed distance field as a scalar field using its distance value. | `ScalarFieldFromSdfNode` |
| Scalar Field Noise | `math.fields.scalar_noise` | Builds a deterministic pseudo-noise scalar field over world space. | `ScalarFieldNoiseNode` |
| Scalar Field Binary Op | `math.fields.scalar_binary_op` | Combines two scalar fields with a basic arithmetic operation. | `ScalarFieldBinaryOpNode` |
| Vector Field Constant | `math.fields.vector_constant` | Builds a vector field that returns a constant vector everywhere. | `VectorFieldConstantNode` |
| Vector Field From SDF Gradient | `math.fields.vector_from_sdf_gradient` | Builds a vector field from central-difference gradients of an SDF (normalized direction). | `VectorFieldFromSdfGradientNode` |
| Vector Field Binary Op | `math.fields.vector_binary_op` | Combines two vector fields component-wise or via cross product. | `VectorFieldBinaryOpNode` |
| Point Attractor Field | `math.fields.point_attractor_field` | Builds a vector field that pulls points toward a center with configurable distance falloff. | `PointAttractorFieldNode` |
| Scalar Field Sample Point | `math.fields.scalar_sample_point` | Samples a scalar field at a point. | `ScalarFieldSamplePointNode` |
| Curve Attractor Field | `math.fields.curve_attractor_field` | Builds a vector field that pulls points toward the closest point on a sampled curve. | `CurveAttractorFieldNode` |
| Scalar Field Sample Points | `math.fields.scalar_sample_points` | Samples a scalar field for each query point and outputs a value list. | `ScalarFieldSamplePointsNode` |
| Vector Field Sample Point | `math.fields.vector_sample_point` | Samples a vector field at a point. | `VectorFieldSamplePointNode` |
| Volume Attractor Field | `math.fields.volume_attractor_field` | Builds a volume-based attractor field using center-pull or nearest-surface pull from geometry/SDF inputs. | `VolumeAttractorFieldNode` |
| Vector Field Sample Points | `math.fields.vector_sample_points` | Samples a vector field for each query point and outputs a vector list. | `VectorFieldSamplePointsNode` |
| Vortex Field | `math.fields.vortex_field` | Builds a tangential swirl field around an axis, with radial falloff and clockwise/counter-clockwise control. | `VortexFieldNode` |
| Repulsor Field | `math.fields.repulsor_field` | Inverts a vector field direction (repulsion) with optional strength scaling. | `RepulsorFieldNode` |
| Attractor Field Blend | `math.fields.attractor_blend` | Blends up to four attractor/repulsor fields using per-field weights. | `AttractorFieldBlendNode` |

## math.list (22)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Create List | `math.list.create_list` | Packs multiple input items into a single list. | `CreateListNode` |
| Zip Lists | `math.list.zip` | Pairs two lists by index and outputs tuples as entry maps. | `ZipListsNode` |
| Deduplicate List | `math.list.deduplicate` | Removes duplicate values from a list while preserving order. | `DeduplicateListNode` |
| List Statistics | `math.list.statistics` | Computes min, max, sum, average, and median for a numeric list. | `ListStatisticsNode` |
| Map List | `math.list.map_list` | Applies a scalar operation to each numeric item in a list. | `MapListNode` |
| Reduce List | `math.list.reduce` | Reduces a list into a single value using a selected operation. | `ReduceListNode` |
| Chunk List | `math.list.chunk` | Splits a list into fixed-size chunks. | `ChunkListNode` |
| Transpose List of Lists | `math.list.transpose` | Transposes a list of lists by swapping rows and columns. | `TransposeListNode` |
| Combine Lists | `math.list.combine_lists` | Combines multiple lists into a single list by index. | `CombineListsNode` |
| Dispatch List | `math.list.dispatch_list` | Splits a list into two based on boolean conditions | `DispatchListNode` |
| Filter List | `math.list.filter_list` | Filters a list based on boolean conditions | `FilterListNode` |
| Flatten List | `math.list.flatten_list` | Flattens a nested list structure into a single-level list | `FlattenListNode` |
| Get Item | `math.list.get_item` | Gets an item from a list at a specified index. | `GetItemNode` |
| Group List | `math.list.group_list` | Groups items in a list based on a key list | `GroupListNode` |
| Insert Item | `math.list.insert_item` | Inserts an item into a list at a specified index | `InsertItemNode` |
| List Length | `math.list.list_length` | Returns the number of items in a list. | `ListLengthNode` |
| Remove Item | `math.list.remove_item` | Removes an item from a list by index or value | `RemoveItemNode` |
| Reverse List | `math.list.reverse_list` | Reverses the order of elements in a list. | `ReverseListNode` |
| Set Item | `math.list.set_item` | Sets an item in a list at a specified index | `SetItemNode` |
| Shuffle List | `math.list.shuffle_list` | Randomly reorders elements in a list | `ShuffleListNode` |
| Sort List | `math.list.sort_list` | Sorts elements of a list | `SortListNode` |
| Sub List | `math.list.sub_list` | Gets a portion of a list between start and end indexes. | `SubListNode` |

## math.logic (6)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| If | `math.logic.if` | Chooses between true and false values based on a condition. | `IfNode` |
| Switch | `math.logic.switch` | Selects one of multiple inputs by index, with a default fallback. | `SelectItemNode` |
| AND | `math.logic.and` | Returns true only when both inputs evaluate to true. | `AndNode` |
| OR | `math.logic.or` | Returns true when either input evaluates to true. | `OrNode` |
| NOT | `math.logic.not` | Returns the negated boolean value of the input. | `NotNode` |
| XOR | `math.logic.xor` | Returns true only when exactly one input evaluates to true. | `XorNode` |

## math.random (4)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Random Number | `math.random.random_number` | Generates random numbers within a specified range. | `RandomNumberNode` |
| Noise | `math.random.noise` | Samples coherent noise from a 3D position and seed. | `NoiseNode` |
| Random List Item | `math.random.random_list_item` | Randomly selects one or more items from a list. | `RandomListItemNode` |
| Random Vector | `math.random.random_vector` | Generates random vectors within a specified bounding box. | `RandomVectorNode` |

## math.scalar_math (23)

| Node Name | Node ID | Description | Class |
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
| Clamp | `math.scalar_math.clamp` | Restricts a value to the specified minimum and maximum values. | `ClampNode` |
| Remap | `math.scalar_math.remap` | Maps a value from an input range to an output range. | `RemapNode` |
| Floor | `math.scalar_math.floor` | Rounds a value down to the nearest integer. | `FloorNode` |
| Ceiling | `math.scalar_math.ceiling` | Rounds a value up to the nearest integer. | `CeilingNode` |
| Round | `math.scalar_math.round` | Rounds a value to the nearest integer. | `RoundNode` |
| Square Root | `math.scalar_math.sqrt` | Computes the square root of a numeric input. | `SqrtNode` |
| Lerp | `math.scalar_math.lerp` | Linearly interpolates between A and B using parameter T. | `LerpNode` |
| Integer Divide | `math.scalar_math.int_divide` | Performs floor-style integer division A / B and returns quotient and remainder. | `IntDivideNode` |
| Smoothstep | `math.scalar_math.smoothstep` | Computes smooth Hermite interpolation 3t^2 - 2t^3 between edge0 and edge1. | `SmoothstepNode` |
| Sign | `math.scalar_math.sign` | Returns -1, 0, or +1 based on the sign of the input value. | `SignNode` |
| Fraction (Frac) | `math.scalar_math.frac` | Returns the fractional part of x as x - floor(x). | `FracNode` |
| Graph Mapper | `math.scalar_math.graph_mapper` | Maps a value through a selectable normalized graph function, similar to Grasshopper's Graph Mapper | `GraphMapperNode` |
| Expression | `math.scalar_math.expression` | Evaluates a numeric expression using input variables such as A, B, C, X, Y, Z, and T | `ExpressionNode` |

## math.sequence (3)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Range | `math.sequence.range` | Generates a numeric sequence from Start to End using Step. | `MathRangeNode` |
| Data Series | `math.sequence.series` | Generates a series of numbers with constant increment | `DataSeriesNode` |
| Repeat | `math.sequence.repeat` | Repeats a single data item or list multiple times | `RepeatNode` |

## math.trigonometry (14)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Sine (Sin) | `math.trigonometry.sin` | 计算角度的正弦值（输入为弧度） | `SineNode` |
| Cosine (Cos) | `math.trigonometry.cos` | 计算角度的余弦值（输入为弧度） | `CosineNode` |
| Tangent (Tan) | `math.trigonometry.tan` | 计算角度的正切值（输入为弧度） | `TangentNode` |
| Degrees To Radians | `math.trigonometry.deg_to_rad` | 将角度从度数转换为弧度 | `DegreesToRadiansNode` |
| Radians To Degrees | `math.trigonometry.rad_to_deg` | 将角度从弧度转换为度数 | `RadiansToDegreesNode` |
| Arcsine (ArcSin) | `math.trigonometry.asin` | 计算输入值的反正弦值（结果以弧度为单位） | `ArcSinNode` |
| Arccosine (ArcCos) | `math.trigonometry.acos` | 计算输入值的反余弦值（结果以弧度为单位） | `ArcCosNode` |
| Arctangent (ArcTan) | `math.trigonometry.atan` | 计算输入值的反正切值（结果以弧度为单位） | `ArcTanNode` |
| Atan2 | `math.trigonometry.atan2` | Computes the signed angle in radians from X and Y using atan2(Y, X). | `Atan2Node` |
| Pi | `math.trigonometry.pi` | 输出数学常数π的值 | `PiNode` |
| E | `math.trigonometry.e` | Outputs the mathematical constant e (approximately 2.718281828...). | `ENode` |
| Sinh | `math.trigonometry.sinh` | Computes the hyperbolic sine of the input value. | `SinhNode` |
| Cosh | `math.trigonometry.cosh` | Computes the hyperbolic cosine of the input value. | `CoshNode` |
| Tanh | `math.trigonometry.tanh` | Computes the hyperbolic tangent of the input value. | `TanhNode` |

## output.debug (4)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Value Monitor | `output.debug.value_monitor` | 将任意输出连到输入，在面板上查看该输出的数据和类型 | `ValueMonitorNode` |
| Print To Chat | `output.debug.print_to_chat` | 将输入数据显示到游戏聊天框 | `PrintToChatNode` |
| Execution Timer | `output.debug.execution_timer` | 测量连接到此节点的计算分支所花费的时间 | `ExecutionTimerNode` |
| Panel | `output.debug.data_inspector` | 显示连接到其输入端口的原始数据（文本形式） | `PanelNode` |

## output.execute (8)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Apply Changes | `output.execute.apply_changes` | Applies explicit placements, placement trees, or voxelized geometry to the world. | `ApplyChangesNode` |
| Clear Preview | `output.execute.clear_preview` | Clears all active previews | `ClearAllPreviewsNode` |
| Bake Geometry To Blocks | `output.execute.bake_geometry_to_blocks` | Bakes any supported geometry into Minecraft block coordinates for final execution | `GeometryToBlocksNode` |
| SDF To Blocks | `output.execute.sdf_to_blocks` | Voxelizes a signed distance field directly into Minecraft block coordinates | `SdfToBlocksNode` |
| Undo Last Bake | `output.execute.undo_last_bake` | Reverts the most recent recorded bake or apply-changes operation | `UndoLastBakeNode` |
| Redo Last Bake | `output.execute.redo_last_bake` | Reapplies the most recently undone bake or apply-changes operation | `RedoLastBakeNode` |
| Bake Surface Strip To Blocks | `output.execute.bake_surface_strip_to_blocks` | Bakes a surface strip into block coordinates for final execution | `SurfaceStripToBlocksNode` |
| Merge Block Placements | `output.execute.merge_block_placements` | Merges block placement lists and placement trees into execution-ready placements | `MergeBlockPlacementsNode` |

## output.export (4)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Export Schematic | `output.export.export_schematic` | Exports placements to a NodeCraft NBT structure file | `ExportSchematicNode` |
| Export Litematic | `output.export.export_litematic` | Exports placements to a single-region Litematic file | `ExportLitematicNode` |
| Export WorldEdit | `output.export.export_worldedit` | Exports placements to a Sponge schematic file for WorldEdit | `ExportWorldEditNode` |
| Export CSV / JSON | `output.export.export_data` | Exports list/coordinates data to CSV or JSON file. | `ExportDataNode` |

## output.preview (12)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Geometry Viewer | `output.preview.geometry_viewer` | Previews geometry visually without committing changes to the world, with optional LOD sampling, bounding-box labels, pivot axes, and dominant direction vectors. | `GeometryViewerNode` |
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
| Preview Geometry | `output.preview.preview_geometry` | Previews analytic geometry directly (semi-transparent fill + outline) before voxelization | `PreviewGeometryNode` |

## pattern.grid (5)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Grid Array | `pattern.grid.grid_array` | 在平面或三维网格上重复坐标列表 | `GridArrayNode` |
| Facade Grid | `pattern.grid.facade_grid` | Generates facade cell centers and boundaries on a box face | `FacadeGridNode` |
| Grid Array Geometry | `pattern.grid.grid_array_geometry` | Creates rectangular or box arrays of geometry using X, Y, and optional Z directions | `GridArrayGeometryNode` |
| Hex Grid | `pattern.grid.hex_grid` | Repeats coordinates on a flat-top hexagonal lattice (X/Z) with configurable spacing | `HexGridNode` |
| Triangular Grid | `pattern.grid.triangle_grid` | Repeats coordinates on a triangular lattice with alternating row offsets. | `TriangularGridNode` |

## pattern.linear (7)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Linear Array | `pattern.linear.linear_array` | 将坐标列表沿直线方向重复排列 | `LinearArrayNode` |
| Along Path | `pattern.linear.along_path` | Repeats a block pattern at each resolved path point from a line, polyline, curve, or point list | `AlongPathNode` |
| Staggered Array | `pattern.linear.staggered_array` | Repeats coordinates in rows and applies an alternating offset for brick-like staggering | `StaggeredArrayNode` |
| Path Instances | `pattern.linear.path_instances` | Generates path instance frames (origin + axes) for oriented placement along a path. | `PathInstancesNode` |
| Instance on Points | `pattern.linear.instance_on_points` | Instances a block or block-placement template at each input point. | `InstanceOnPointsNode` |
| Linear Array Geometry | `pattern.linear.linear_array_geometry` | Creates repeated geometry copies along a direction vector | `LinearArrayGeometryNode` |
| Curve Array Geometry | `pattern.linear.curve_array_geometry` | Creates repeated geometry copies along a curve, polyline, or line path with optional tangent orientation | `CurveArrayGeometryNode` |

## pattern.lsystem (2)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| L-System Expand String | `pattern.lsystem.expand_string` | Expands an L-system axiom using production rules for a fixed number of iterations (longest symbol match; probabilities as weights) | `LSystemExpandStringNode` |
| L-System Turtle 3D | `pattern.lsystem.turtle_3d` | Traces a 3D polyline from L-system commands: F/f forward, +- yaw, & and ^ pitch, / and \\ roll, [] stack (local turns; angle in degrees) | `LSystemTurtle3DNode` |

## pattern.radial (4)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Polar Array | `pattern.radial.polar_array` | 将坐标列表绕中心点重复旋转排列 | `PolarArrayNode` |
| Spiral Array | `pattern.radial.spiral_array` | Repeats coordinates along a spiral path around a center point | `SpiralArrayNode` |
| Phyllotaxis | `pattern.radial.phyllotaxis` | Repeats coordinates using sunflower-like golden-angle distribution. | `PhyllotaxisNode` |
| Polar Array Geometry | `pattern.radial.polar_array_geometry` | Creates repeated geometry copies around a center point and axis | `PolarArrayGeometryNode` |

## pattern.surface_volume_distribution (9)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Populate Region | `pattern.surface_volume_distribution.populate_region` | 在指定区域内随机或均匀生成坐标列表 | `PopulateRegionNode` |
| Sample Sphere Surface | `pattern.surface_volume_distribution.sample_surface` | Samples points and normals on a sphere surface for scattering and growth workflows | `SampleSphereSurfaceNode` |
| Sample Geometry Surface | `pattern.surface_volume_distribution.sample_geometry_surface` | Samples points from voxelized geometry surfaces using density or explicit count | `SampleGeometrySurfaceNode` |
| Scatter On Sphere Surface | `pattern.surface_volume_distribution.surface_scatter` | Scatters points on a sphere surface and outputs matching normals and optional snapped block coordinates | `ScatterOnSphereSurfaceNode` |
| Poisson Disk On Plane | `pattern.surface_volume_distribution.poisson_disk_plane` | Samples points on a plane inside a UV rectangle with minimum separation using rejection sampling | `PoissonDiskOnPlaneNode` |
| Scatter On Geometry Surface | `pattern.surface_volume_distribution.scatter_geometry_surface` | Scatters points on voxelized geometry surfaces with random or blue-noise approximation and spacing fallback controls | `ScatterOnGeometrySurfaceNode` |
| Scatter On Surface Strip | `pattern.surface_volume_distribution.scatter_surface_strip` | Scatters points on a surface strip by random section interpolation with optional spacing | `ScatterOnSurfaceStripNode` |
| Scatter In Volume | `pattern.surface_volume_distribution.scatter_volume` | Scatters points inside voxelized geometry volume with random or blue-noise approximation. | `ScatterInVolumeNode` |
| Image-Based Scatter | `pattern.surface_volume_distribution.image_scatter` | Scatters points using image grayscale density maps on a plane or world XZ. | `ImageBasedScatterNode` |

## pattern.voronoi_3d (1)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Voronoi 3D Lloyd Relax (Grid) | `pattern.voronoi_3d.lloyd_relax` | Approximate 3D Lloyd relaxation: grid cell centers vote for nearest site; sites move to cell centroids (repeat). Not an exact Voronoi diagram. | `Voronoi3DLloydRelaxNode` |

## reference.frames (4)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Face Center Frame | `reference.frames.frame_from_face` | Builds a local frame at the center of a box face using the face plane and boundary directions | `FaceCenterFrameNode` |
| Sphere Surface Frame | `reference.frames.frame_along_surface` | Builds a local tangent frame on a sphere using the nearest surface point and outward normal | `SphereSurfaceFrameNode` |
| World Frame | `reference.frames.world_frame` | Outputs the world coordinate frame origin and axis vectors | `WorldFrameNode` |
| Transform Frame | `reference.frames.transform_frame` | Applies translation, rotation, and uniform scale to an input frame | `TransformFrameNode` |

## reference.planes (6)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| World Plane | `reference.planes.world_plane` | Creates a standard XY, YZ, or XZ world plane with a configurable origin | `PlaneSelectorNode` |
| Construct Plane | `reference.planes.construct_plane` | Constructs a plane from an origin point and a normal vector | `ConstructPlaneNode` |
| Construct Plane From Points | `reference.planes.plane_from_points` | Constructs a plane from three non-collinear points | `ConstructPlaneFromPointsNode` |
| Distance Point To Plane | `reference.planes.distance_point_to_plane` | Measures the absolute and signed distance from a geometric point to a plane | `DistancePointToPlaneNode` |
| Box Face To Plane | `reference.planes.block_face_plane` | Explicitly converts a box face into its supporting plane and related face frame data | `BoxFaceToPlaneNode` |
| Offset Plane | `reference.planes.offset_plane` | Offsets a plane along its normal by a signed distance | `OffsetPlaneNode` |

## reference.points (18)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Coordinate Input | `reference.points.point_from_coordinates` | Inputs an integer coordinate and outputs Coordinate, Block Pos, X, Y, and Z. | `CoordinateInputNode` |
| Block To Point | `reference.points.point_from_block` | Explicitly converts a block coordinate into a geometric point, with optional block-center offset | `BlockToPointNode` |
| Construct Coordinate | `reference.points.construct_coordinate` | Constructs a block coordinate from X, Y, and Z integer components. | `ConstructCoordinateNode` |
| Point Along Vector | `reference.points.point_along_vector` | Creates a new point by moving a start point along a direction vector by a distance | `PointAlongVectorNode` |
| Block To Vector | `reference.points.block_to_vector` | Explicitly converts a block coordinate into a Vector3d position, with optional block-center offset. | `BlockToVectorNode` |
| Deconstruct Coordinate | `reference.points.deconstruct_point` | Extracts X, Y, and Z integer components from a block coordinate. | `DeconstructCoordinateNode` |
| Mid Point | `reference.points.mid_point` | Computes the midpoint between two input points | `MidpointNode` |
| Distance Between Points | `reference.points.distance_between_points` | Computes the distance between two input points | `DistanceNode` |
| Closest Point | `reference.points.closest_point` | Finds the closest point in a point collection to a reference point. | `ClosestPointNode` |
| Point List Center | `reference.points.point_list_center` | Calculates the average geometric center of a point list | `PointListCenterNode` |
| Point List Bounds | `reference.points.point_list_bounds` | Calculates an axis-aligned bounding box from a list of geometric points | `PointListBoundsNode` |
| Get Box Corner | `reference.points.get_box_corner` | Gets a single corner from box geometry by index | `GetBoxCornerNode` |
| Get Box Face | `reference.points.get_box_face` | Gets a single face from box geometry by semantic name or index | `GetBoxFaceNode` |
| Get Face Edge | `reference.points.get_face_edge` | Gets a single edge from a face by index | `GetFaceEdgeNode` |
| Deconstruct Box Face | `reference.points.deconstruct_face` | Extracts corners, edges, plane, center, and normal from a box face | `DeconstructBoxFaceNode` |
| Deconstruct Face Edge | `reference.points.deconstruct_edge` | Extracts endpoints, midpoint, direction, vector, and length from a face edge | `DeconstructFaceEdgeNode` |
| Project Point To Polyline | `reference.points.project_to_polyline` | Projects a point onto the closest location on a polyline or line segment | `ProjectPointToPolylineNode` |
| Closest Point To Object | `reference.points.closest_point_to_object` | Finds the nearest point from a query point to a curve, path, surface strip, or voxelized geometry | `ClosestPointToObjectNode` |

## reference.vectors (18)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Vector Input | `reference.vectors.vector` | Inputs a 3D vector from panel values or optional X/Y/Z input ports, then outputs the vector and components. | `VectorInputNode` |
| 2D Vector Input | `reference.vectors.vector2_input` | Inputs a 2D vector (X/Y or U/V) and outputs vector + components. | `Vector2InputNode` |
| Construct Vector | `reference.vectors.construct_vector` | Constructs a Vector3d from X, Y, and Z components. | `ConstructVectorNode` |
| Deconstruct Vector | `reference.vectors.deconstruct_vector` | Outputs the X, Y, and Z components of a vector. | `DeconstructVectorNode` |
| Normalize Vector | `reference.vectors.normalize_vector` | Normalizes a vector to unit length. | `NormalizeVectorNode` |
| Cross Product | `reference.vectors.cross_product` | Computes the cross product A x B and its magnitude. | `CrossProductNode` |
| Dot Product | `reference.vectors.dot_product` | Computes the dot product of vectors A and B. | `DotProductNode` |
| Vector Length | `reference.vectors.vector_length` | Computes the length (magnitude) of a vector. | `VectorLengthNode` |
| Vector Addition (+) | `reference.vectors.vector_addition` | Computes the vector sum A + B. | `VectorAdditionNode` |
| Vector Subtraction (-) | `reference.vectors.vector_subtraction` | Computes the vector difference A - B. | `VectorSubtractionNode` |
| Vector Scalar Multiply | `reference.vectors.vector_scalar_multiply` | Multiplies a vector by a scalar. | `VectorScalarMultiplyNode` |
| Vector Scalar Divide | `reference.vectors.vector_scalar_divide` | Divides a vector by a scalar. | `VectorScalarDivideNode` |
| Angle Between Vectors | `reference.vectors.angle_between` | Angle between two vectors in radians and degrees; optional reference vector yields a signed angle | `AngleBetweenVectorsNode` |
| Lerp Vectors | `reference.vectors.lerp_vectors` | Linearly interpolates between vector A and B using parameter T. | `LerpVectorsNode` |
| Reflect Vector | `reference.vectors.reflect` | Reflects an input vector around a normal vector using v - 2(v·n)n. | `ReflectVectorNode` |
| Project Vector onto Vector | `reference.vectors.project` | Projects vector A onto vector B as (A·B / \|B\|^2)B. | `ProjectVectorNode` |
| Vector Component Min/Max | `reference.vectors.component_minmax` | Computes per-component min and max between vectors A and B. | `VectorComponentMinMaxNode` |
| Slerp Vectors | `reference.vectors.slerp` | Performs spherical linear interpolation between two direction vectors. | `SlerpVectorsNode` |

## transform.basic_transforms (15)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Offset Coordinate | `transform.basic_transforms.offset_coordinate` | Offsets a single block coordinate by integer X, Y, Z amounts or a rounded vector | `OffsetCoordinateNode` |
| Offset Coordinates | `transform.basic_transforms.offset_coordinates` | Offsets a list of block coordinates by a rounded vector | `OffsetCoordinatesNode` |
| Rotate Coordinates | `transform.basic_transforms.rotate_coordinates` | Rotates a list of block coordinates around a point and axis | `RotateCoordinatesNode` |
| Scale Coordinates | `transform.basic_transforms.scale_coordinates` | Scales a list of block coordinates relative to a center point | `ScaleCoordinatesNode` |
| Mirror Coordinates | `transform.basic_transforms.mirror_coordinates` | Mirrors a block coordinate list across a plane and snaps results to the block grid | `MirrorCoordinatesNode` |
| Offset Box Face | `transform.basic_transforms.offset_face` | Offsets a box face along its normal without modifying the source box geometry | `OffsetBoxFaceNode` |
| Inset Box Face | `transform.basic_transforms.inset_face` | Creates an inset or outset reference face boundary from a box face without modifying the source box | `InsetBoxFaceNode` |
| Mirror Geometry About Plane | `transform.basic_transforms.mirror_geometry_plane` | Mirrors analytic geometry about a plane (recursive for composites and boolean geometry nodes) | `MirrorGeometryAboutPlaneNode` |
| Transform Geometry | `transform.basic_transforms.transform_geometry` | Applies translation, Euler XYZ rotation, and uniform scale to analytic geometry (primitives, composites, booleans, SDF wrappers) | `TransformGeometryNode` |
| Mirror Vector List About Plane | `transform.basic_transforms.mirror_vector_list_plane` | Mirrors each point in a list about a plane and outputs Vector3d positions | `MirrorVectorListAboutPlaneNode` |
| Shear Point List | `transform.basic_transforms.shear` | Applies axial shear deformation to a point list around an origin. | `ShearPointListNode` |
| Transform Points by Frames | `transform.basic_transforms.transform_by_frames` | Transforms local points by frame origin and basis axes into world-space positions. | `TransformPointsByFramesNode` |
| Move Geometry | `transform.basic_transforms.move_geometry` | Moves analytic geometry by a translation vector | `MoveGeometryNode` |
| Rotate Geometry Around Axis | `transform.basic_transforms.rotate_geometry_axis` | Rotates analytic geometry around a center point and arbitrary axis | `RotateGeometryAroundAxisNode` |
| Scale Geometry Around Point | `transform.basic_transforms.scale_geometry_point` | Uniformly scales analytic geometry around a center point | `ScaleGeometryAroundPointNode` |

## transform.deformations (10)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Twist Point List | `transform.deformations.twist` | Twists a point list around an axis by distributing rotation along a specified axial length | `TwistPointListNode` |
| Bend Point List | `transform.deformations.bend` | Bends a point list into an arc along an axis over a configurable bend length | `BendPointListNode` |
| Taper Point List | `transform.deformations.taper` | Scales radial distance along an axis to create tapered forms | `TaperPointListNode` |
| Noise Displace Point List | `transform.deformations.noise_displace` | Applies deterministic pseudo-noise displacement to a point list | `NoiseDisplacePointListNode` |
| Curve Attract Point List | `transform.deformations.curve_attract` | Pulls points toward a sampled curve with quadratic falloff; optional displacement along full vector, tangent only, or perpendicular-to-tangent only | `CurveAttractPointListNode` |
| Relax Point List | `transform.deformations.relax_points` | Laplacian-style smoothing using k nearest neighbors (uniform grid hash for speed; capped point count) | `RelaxPointListNode` |
| Lattice Deform Point List | `transform.deformations.lattice_deform` | Free-form deformation: trilinear blend of control displacements on a uniform (nx+1)(ny+1)(nz+1) lattice in an axis-aligned box | `LatticeDeformPointListNode` |
| Spherical Displace | `transform.deformations.spherical_displace` | Applies radial displacement with spherical distance falloff around a center point. | `SphericalDisplaceNode` |
| Twist Geometry | `transform.deformations.twist_geometry` | Applies an axial twist domain deformation to SDF or geometry, outputting a twisted SDF-backed Geometry | `TwistGeometryNode` |
| Bend Geometry | `transform.deformations.bend_geometry` | Applies an axial bend domain deformation to SDF or geometry before voxelization | `BendGeometryNode` |

## transform.orientation (6)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Project Point To Plane | `transform.orientation.project_to_plane` | Projects a geometric point onto a plane and reports the projection distance | `ProjectPointToPlaneNode` |
| Rotate Vector | `transform.orientation.rotate_vector` | Rotates a vector around an axis by an angle in radians | `RotateVectorNode` |
| Align Points To Surface Normals | `transform.orientation.align_to_surface` | Builds oriented frames per point by aligning local up axis to surface normals. | `AlignPointsToSurfaceNormalsNode` |
| Project Points To Plane | `transform.orientation.project_points_to_plane` | Projects a list of points or vectors onto a target plane | `ProjectPointsToPlaneNode` |
| Project Curve To Plane | `transform.orientation.project_curve_to_plane` | Projects a curve, polyline, or line onto a target plane | `ProjectCurveToPlaneNode` |
| Project Profile To Plane | `transform.orientation.project_profile_to_plane` | Projects a polygon profile boundary onto a target plane | `ProjectProfileToPlaneNode` |

## utilities.assist (6)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| String Format | `utilities.assist.string_format` | Formats strings with placeholders like {0}, {1} from dynamic values. | `StringFormatNode` |
| Assert / Validate | `utilities.assist.assert` | Validates a boolean condition and optionally throws to stop execution when it fails. | `AssertNode` |
| Reroute | `utilities.assist.reroute` | 用于整理连线的中继节点，仅透传输入到输出 | `RerouteNode` |
| Signal Fork | `utilities.assist.signal_fork` | 将一路输入透传到两路输出，便于连线分流 | `SignalForkNode` |
| Signal Merge | `utilities.assist.signal_merge` | 将两路输入按优先级汇聚为一路输出 | `SignalMergeNode` |
| Tag Relay | `utilities.assist.tag_relay` | Passes a signal through while adding a visual semantic tag. | `TagRelayNode` |

## utilities.fileio (3)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Read Image | `utilities.fileio.read_image` | Reads a local image file and outputs dimensions, colors, and grayscale samples | `ReadImageNode` |
| Import VOX | `utilities.fileio.import_vox` | Imports MagicaVoxel .vox files as block coordinates, placements, colors, and palette indices | `ImportVoxNode` |
| Image Sampler | `utilities.fileio.image_sampler` | Samples color, channels, and grayscale values from image pixels using UV or pixel coordinates | `ImageSamplerNode` |

## utilities.morphology (1)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Block List Morphology | `utilities.morphology.block_list_morphology` | Dilates or erodes a block list using 6- or 26-neighbor morphology iterations (Connectivity property) | `BlockListMorphologyNode` |

## utilities.organization (7)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Graph Input | `utilities.organization.graph_input` | Defines a named graph-level input with optional override and default fallback. | `GraphInputNode` |
| Graph Output | `utilities.organization.graph_output` | Defines a named graph-level output and publishes it into execution context. | `GraphOutputNode` |
| Subgraph | `utilities.organization.subgraph` | Executes a referenced subgraph with named input/output mapping. | `SubgraphNode` |
| Subgraph Register | `utilities.organization.subgraph_register` | Registers a subgraph reference into execution context for Subgraph calls. | `SubgraphRegisterNode` |
| Runtime Preset | `utilities.organization.preset` | Saves, loads, and deletes named runtime presets in execution context. | `NodePresetNode` |
| Comment | `utilities.organization.comment` | 在画布上添加文本注释 | `CommentNode` |
| Group | `utilities.organization.group` | 将选中的节点打包成一个可视化组 | `GroupNode` |

## variable (6)

- `variable` nodes read and write execution-scope state. The graph executor only guarantees order through explicit connections, so connect a Set/Remove/Clear output into downstream nodes when the write must happen first.
- User variable names must not start with `__nodecraft.`; that prefix is reserved for internal runtime data.
- `Exists` reports whether a name is present even when the stored value is `null`. Use Remove Variable to delete a name instead of storing `null` as a deletion marker.
- Variable List hides internal variables by default. Frame Local Variable clears its frame before writing when both Clear Frame and Write are true.

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Set Variable | `variable.set` | Stores a value under a user variable name in the execution scope. Connect an output to downstream nodes when write order matters. | `SetVariableNode` |
| Get Variable | `variable.get` | Reads a value by user variable name from the execution scope. Exists means the name exists, even when its stored value is null. | `GetVariableNode` |
| Variable List | `variable.list` | Lists user variables currently available in the execution scope. | `VariableListNode` |
| Frame Local Variable | `variable.frame_local` | Reads or writes variables in an isolated frame-local namespace. When Clear Frame and Write are both true, the frame is cleared first, then Value is written to Name. | `FrameLocalVariableNode` |
| Remove Variable | `variable.remove` | Removes a user variable from the execution scope. | `RemoveVariableNode` |
| Clear Variables | `variable.clear` | Clears user variables from the execution scope. | `ClearVariablesNode` |

## world.query (11)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Get Light Level | `world.query.get_light_level` | Gets the combined, sky, and block light values for a block position | `GetLightLevelNode` |
| Get Fluid Level | `world.query.get_fluid_level` | Gets the fluid state, type, and level for a block position | `GetFluidLevelNode` |
| Is Grid Point | `world.query.is_grid_point` | Checks whether a geometric point already lies on the block grid without snapping | `IsGridPointNode` |
| Filter Grid Points | `world.query.filter_grid_points` | Splits a point list into grid-aligned points and off-grid points without snapping | `FilterGridPointsNode` |
| Filter Points By Rule | `world.query.filter_points_by_rule` | Filters point sets by height and optional surface slope rules. | `FilterPointsByRuleNode` |
| Point In Region | `world.query.is_point_in_region` | Tests whether the center of a block position lies inside a region. | `IsPointInRegionNode` |
| Raycast | `world.query.raycast` | Casts a ray in world space and returns nearest block/entity hit information. | `RaycastNode` |
| Flood Fill | `world.query.flood_fill` | Runs BFS flood fill from a seed block using 6 or 26-neighbor connectivity. | `FloodFillNode` |
| Get Neighbor Blocks | `world.query.get_neighbors` | Returns axis-ray neighbors or cube-volume neighbors around a center position. | `GetNeighborBlocksNode` |
| Get Entities In Region | `world.query.get_entities_in_region` | Gets entities inside a region with optional filtering | `GetEntitiesInRegionNode` |
| Get Entity | `world.query.get_entity` | Finds an entity by UUID or by type near the current player. | `GetEntityNode` |

## world.read (12)

| Node Name | Node ID | Description | Class |
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

## world.selection (9)

| Node Name | Node ID | Description | Class |
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

## world.terrain (19)

| Node Name | Node ID | Description | Class |
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

## world.write (18)

| Node Name | Node ID | Description | Class |
|---|---|---|---|
| Set Block | `world.write.set_block` | Places one block at one block position, with optional block-entity NBT or SNBT applied after placement | `SetBlockNode` |
| Set Blocks | `world.write.set_blocks` | Sets blocks at explicit coordinates, with optional shared block-entity NBT or SNBT applied after placement | `SetBlocksNode` |
| Fill Region | `world.write.fill_region` | Fills a region with a block | `FillRegionNode` |
| Replace Blocks | `world.write.replace_blocks` | Replaces matching blocks in a region or coordinate list | `ReplaceBlocksNode` |
| Clone Region | `world.write.clone_region` | 复制区域到另一个位置 | `CloneRegionNode` |
| Clear Blocks | `world.write.remove_blocks` | Clears blocks at explicit coordinates by replacing them with air | `RemoveBlocksNode` |
| Set Block NBT | `world.write.set_block_nbt` | Writes or merges NBT data to an existing block entity at a target position. | `SetBlockNbtNode` |
| Undo Last World Write | `world.write.undo_last_write` | Reverts the most recent recorded world.write block placement operation | `UndoLastWorldWriteNode` |
| Redo Last World Write | `world.write.redo_last_write` | Reapplies the most recently undone world.write block operation | `RedoLastWorldWriteNode` |
| Peek Last World Write Undo | `world.write.peek_last_undo` | Inspects the latest world.write undo record and outputs affected count and region bounds | `PeekLastWorldWriteUndoNode` |
| Clear World Write Undo History | `world.write.clear_undo_history` | Clears all recorded world.write undo history entries | `ClearWorldWriteUndoHistoryNode` |
| Apply Redstone Power | `world.write.apply_redstone_power` | Places a temporary redstone power source next to a target block | `ApplyRedstonePowerNode` |
| Execute Command | `world.write.execute_command` | Executes a Minecraft command on the server | `ExecuteCommandNode` |
| Remove Entities | `world.write.remove_entities` | 移除实体 | `RemoveEntitiesNode` |
| Simulate Right Click | `world.write.simulate_right_click` | Simulates a server-side right click on a block | `SimulateRightClickNode` |
| Spawn Entity | `world.write.spawn_entity` | Spawns an entity into the world at a given position | `SpawnEntityNode` |
| Teleport Entity | `world.write.entity_teleport` | 传送实体 | `EntityTeleportNode` |
| Write Sign Text | `world.write.write_sign_text` | Writes text to a sign block entity | `WriteSignTextNode` |

## Notes

- This document is generated from `@NodeInfo` metadata in node classes.
- For nodes without `@NodeInfo`, metadata is inferred from class name and package path.
- To improve node docs quality, fill `description` in each `@NodeInfo` annotation.
