# NodeCraft 节点库

- **统计范围**：`src/main/java/com/nodecraft/nodesystem/nodes`
- **节点总数**：**292**
- **分类总数**：**46**
- **说明**：「节点名称」与「说明」列来自各节点类上的 `@NodeInfo` （与编辑器展示一致），若源码未写注解说明，则该列为 `-`。

## 分类统计

| 分类 ID | 节点数 |
|---|---:|
| `geometry.architectural_primitives` | 1 |
| `geometry.boolean` | 5 |
| `geometry.curves` | 10 |
| `geometry.primitives` | 19 |
| `geometry.profiles` | 8 |
| `geometry.solids` | 17 |
| `input.basic` | 3 |
| `input.context` | 4 |
| `input.numeric` | 8 |
| `input.type_selectors` | 5 |
| `material.basic_assignment` | 2 |
| `material.block_state` | 3 |
| `material.directional_mapping` | 2 |
| `material.gradient_mapping` | 3 |
| `material.pattern_mapping` | 4 |
| `material.surface_aging` | 3 |
| `math.compare` | 7 |
| `math.sequence` | 3 |
| `math.list` | 16 |
| `math.logic` | 6 |
| `math.random` | 4 |
| `math.scalar_math` | 15 |
| `math.trigonometry` | 9 |
| `output.debug` | 4 |
| `output.execute` | 15 |
| `output.export` | 3 |
| `output.preview` | 12 |
| `pattern.grid` | 2 |
| `pattern.linear` | 2 |
| `pattern.radial` | 1 |
| `pattern.surface_volume_distribution` | 4 |
| `reference.frames` | 2 |
| `reference.planes` | 5 |
| `reference.points` | 16 |
| `reference.vectors` | 11 |
| `transform.basic_transforms` | 9 |
| `transform.deformations` | 1 |
| `transform.orientation` | 2 |
| `utilities.assist` | 4 |
| `utilities.fileio` | 1 |
| `utilities.morphology` | 1 |
| `utilities.organization` | 3 |
| `world.query` | 7 |
| `world.read` | 9 |
| `world.selection` | 8 |
| `world.write` | 13 |

## geometry.architectural_primitives（1）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Window Array | `geometry.architectural_primitives.window_array` | Generates a rectangular array of inset window opening boxes on a box face | `WindowArrayNode` |

## geometry.boolean（5）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Bounding Box | `geometry.boolean.bounding_box` | Calculates an axis-aligned bounding box from a block list or region | `BoundingBoxNode` |
| Geometry Bounds | `geometry.boolean.geometry_bounds` | Calculates an axis-aligned bounding box from any supported geometry | `GeometryBoundsNode` |
| Geometry Union | `geometry.boolean.union` | Combines multiple geometry inputs into one composite geometry value | `GeometryUnionNode` |
| Difference | `geometry.boolean.difference` | Creates a voxel-evaluated difference geometry value by subtracting cutter geometry from a base geometry | `DifferenceNode` |
| Intersection | `geometry.boolean.intersection` | Creates a voxel-evaluated intersection geometry value from two geometry inputs | `IntersectionNode` |

## geometry.curves（10）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Points To Path | `geometry.curves.curve_from_points` | Builds a line or polyline from an ordered point list | `PointsToPathNode` |
| Path To Points | `geometry.curves.divide_curve_to_points` | Extracts an ordered point list from a line, polyline, or curve | `PathToPointsNode` |
| Arc | `geometry.curves.arc` | Builds a sampled circular arc from a center point, plane, radius, and start/end angles | `ArcNode` |
| Face Edge To Path | `geometry.curves.edge_to_curve` | Converts a face edge into line, polyline, and point outputs for path workflows | `FaceEdgeToPathNode` |
| Bezier | `geometry.curves.bezier` | Builds a sampled Bezier curve from an ordered list of control points | `BezierNode` |
| Box Face Boundary Path | `geometry.curves.face_boundary_curve` | Builds a closed boundary path from a box face for preview and downstream path workflows | `BoxFaceBoundaryPathNode` |
| Offset Polyline In Plane | `geometry.curves.offset_polyline_plane` | Offsets a polyline in a plane using parallel segments and miters (left is CCW in the plane UV basis) | `PolylineOffsetInPlaneNode` |
| Fillet Polyline Corners | `geometry.curves.fillet_polyline_corners` | Fillets interior corners of an open polyline with circular arcs in the work plane | `PolylineCornerFilletNode` |
| Resample Polyline By Length | `geometry.curves.resample_polyline_length` | Resamples a polyline along its arc length using spacing, or using a total point count (count wins when both are provided) | `ResamplePolylineByLengthNode` |
| Polyline Length | `geometry.curves.polyline_length` | Computes the total length of a polyline or line segment | `PolylineLengthNode` |

## geometry.primitives（19）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Box by Center + Size | `geometry.primitives.box` | Generates a box from a center point and explicit X/Y/Z sizes | `BoxCenterSizeNode` |
| Box by Corner + Size | `geometry.primitives.box_from_corner_size` | Generates a box from one anchor corner and signed X/Y/Z sizes. Negative values grow in the opposite local axis direction. | `BoxCornerSizeNode` |
| Box by Two Corners | `geometry.primitives.box_from_corners` | Generates an axis-aligned box from two opposite corner points | `BoxCornersNode` |
| Sphere By Center Radius | `geometry.primitives.sphere` | Constructs sphere geometry from a center point and radius | `SphereByCenterRadiusNode` |
| Sphere By Diameter | `geometry.primitives.sphere_from_diameter` | Constructs sphere geometry from two diameter endpoints | `SphereByDiameterNode` |
| Cylinder By Axis Radius | `geometry.primitives.cylinder` | Constructs cylinder geometry from two axis endpoints and a radius | `CylinderByAxisRadiusNode` |
| Cone By Base Apex Radius | `geometry.primitives.cone` | Constructs cone geometry from a base center, apex point, and base radius | `ConeByBaseApexRadiusNode` |
| Ellipsoid By Center Radii | `geometry.primitives.ellipsoid` | Constructs ellipsoid geometry from a center point and X/Y/Z radii | `EllipsoidByCenterRadiiNode` |
| Octahedron By Center Size | `geometry.primitives.octahedron` | Constructs octahedron geometry from a center point and vertex radius | `OctahedronByCenterSizeNode` |
| Tetrahedron By Center Edge | `geometry.primitives.tetrahedron` | Constructs tetrahedron geometry from a center point and edge length | `TetrahedronByCenterEdgeNode` |
| Deconstruct Box Geometry | `geometry.primitives.deconstruct_box` | Extracts center, half extents, orientation, corners, and faces from box geometry | `DeconstructBoxGeometryNode` |
| Square Pyramid | `geometry.primitives.square_pyramid` | Constructs square pyramid geometry from a base center, base size, height, and plane | `SquarePyramidNode` |
| Deconstruct Sphere | `geometry.primitives.deconstruct_sphere` | Extracts center, radius, diameter, bounds, area, and volume from sphere geometry | `DeconstructSphereNode` |
| Deconstruct Cylinder | `geometry.primitives.deconstruct_cylinder` | Extracts axis, radius, height, bounds, and analytical values from cylinder geometry | `DeconstructCylinderNode` |
| Deconstruct Cone | `geometry.primitives.deconstruct_cone` | Extracts axis, height, radius, bounds, and analytical values from cone geometry | `DeconstructConeNode` |
| Deconstruct Ellipsoid | `geometry.primitives.deconstruct_ellipsoid` | Extracts center, radii, bounds, volume, and approximate surface area from ellipsoid geometry | `DeconstructEllipsoidNode` |
| Deconstruct Octahedron | `geometry.primitives.deconstruct_octahedron` | Extracts center, size, vertices, bounds, and analytical values from octahedron geometry | `DeconstructOctahedronNode` |
| Deconstruct Tetrahedron | `geometry.primitives.deconstruct_tetrahedron` | Extracts center, edge length, vertices, bounds, and analytical values from tetrahedron geometry | `DeconstructTetrahedronNode` |
| Deconstruct Prism | `geometry.primitives.deconstruct_prism` | Extracts base polygon, top polygon, extrusion, side surface strip, and bounds from prism geometry | `DeconstructPrismNode` |

## geometry.profiles（8）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Rectangle On Plane | `geometry.profiles.rectangle_profile` | Constructs a planar rectangle from a center point, width, height, and plane | `RectangleOnPlaneNode` |
| Regular Polygon On Plane | `geometry.profiles.polygon_profile` | Constructs a regular polygon from a center point, radius, side count, and plane | `RegularPolygonOnPlaneNode` |
| Polygon By Points | `geometry.profiles.custom_profile` | Constructs a planar polygon profile from an ordered point list | `PolygonByPointsNode` |
| Resample Polygon Profile | `geometry.profiles.resample_profile` | Resamples a polygon profile to a target edge count using perimeter-distance sampling | `ResamplePolygonProfileNode` |
| Deconstruct Polygon Profile | `geometry.profiles.deconstruct_profile` | Extracts points, boundary, plane, center, perimeter, and area from a polygon profile | `DeconstructPolygonProfileNode` |
| Convex Hull 2D On Plane | `geometry.profiles.convex_hull_plane` | Projects points into a plane, computes their 2D convex hull, and outputs a closed polygon profile | `ConvexHull2DOnPlaneNode` |
| Voronoi Cells 2D On Plane | `geometry.profiles.voronoi_cells_plane` | Projects sites into a plane, builds a clipped planar Voronoi diagram (JTS), and outputs each cell as a polygon profile on the plane | `VoronoiCells2DOnPlaneNode` |
| Convex Hull 3D From Points | `geometry.profiles.convex_hull_3d_points` | Builds a 3D convex hull (triangle facets) from points; intended for small clouds due to brute-force enumeration; coplanar / collinear inputs yield no facets | `ConvexHull3DFromPointsNode` |

## geometry.solids（17）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Extrude Profile | `geometry.solids.extrude` | Extrudes a polygon profile by a direction vector into a top profile and side surface strip | `ExtrudeProfileNode` |
| Extrude Point List | `geometry.solids.extrude_from_points` | Extrudes an ordered point list by a direction vector and emits source path, top path, and side segments | `ExtrudePointListNode` |
| Extrude Box Face | `geometry.solids.extrude_box_face` | Extrudes a box face into a new box segment and returns a composite geometry | `ExtrudeBoxFaceNode` |
| Loft Profiles | `geometry.solids.loft` | Lofts two polygon profiles with matching edge counts into a side surface strip | `LoftProfilesNode` |
| Loft Point Lists | `geometry.solids.loft_from_points` | Connects two ordered point lists and emits source paths, target paths, and loft rail segments | `LoftPointListsNode` |
| Sweep Profile Along Path | `geometry.solids.sweep` | Sweeps a polygon profile along a path and emits section profiles plus a side surface strip | `SweepProfileAlongPathNode` |
| Sweep Point List Along Path | `geometry.solids.sweep_from_points` | Sweeps an ordered point profile along a path and emits section paths plus connecting rail segments | `SweepPointListAlongPathNode` |
| Revolve Profile | `geometry.solids.revolve` | Revolves a polygon profile around an axis and emits section profiles plus a side surface strip | `RevolveProfileNode` |
| Surface Strip To Geometry | `geometry.solids.surface_strip_to_geometry` | Explicitly converts a surface strip into reusable geometry by sampling section edges and rails as cylinders | `SurfaceStripToGeometryNode` |
| Push/Pull Box Face | `geometry.solids.push_pull_face` | Moves one box face along its normal and outputs a new box geometry | `PushPullBoxFaceNode` |
| Shell Surface Strip | `geometry.solids.shell` | Builds inner and outer offset shell layers from a surface strip and emits cap strips plus an optional geometry approximation | `ShellNode` |
| Prism By Profile Vector | `geometry.solids.extrude_profile` | Constructs prism geometry from a polygon profile and an extrusion vector | `PrismByProfileVectorNode` |
| Thicken Surface | `geometry.solids.thicken_surface` | Thickens a surface strip into two offset layers with optional cap strips and a reusable geometry approximation | `ThickenSurfaceNode` |
| Prism By Base Points Vector | `geometry.solids.extrude_profile_from_points` | Constructs prism geometry from an ordered base polygon and an extrusion vector | `PrismByBasePointsVectorNode` |
| Deconstruct Surface Strip | `geometry.solids.deconstruct_surface_strip` | Breaks a surface strip into section paths, flattened points, and rail segments | `DeconstructSurfaceStripNode` |
| Shrinkwrap Points On Surface Strip | `geometry.solids.shrinkwrap_points_surface_strip` | Projects each query point to the closest location on the surface strip triangle mesh | `ShrinkwrapPointsOnSurfaceStripNode` |
| Shrinkwrap Points To Voxel Geometry | `geometry.solids.shrinkwrap_points_voxel_geometry` | Voxelizes geometry to blocks, then snaps each query point to the nearest voxel block center (shell when fill is off); distinct from triangle strip shrinkwrap | `ShrinkwrapPointsToVoxelGeometryNode` |

## input.basic（3）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Text Input | `input.basic.text_input` | Allows entering single-line or multi-line text. | `TextInputNode` |
| Color Picker | `input.basic.color_picker` | Allows selecting a color value with RGB and alpha support. | `ColorPickerNode` |
| Boolean Toggle | `input.basic.boolean_toggle` | Provides a toggle control that outputs a boolean value | `BooleanToggleNode` |

## input.context（4）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Player Position | `input.context.player_position` | Gets the player's current world position. | `PlayerPositionNode` |
| Player Look At | `input.context.player_look_direction` | Gets the player's look direction and current raycast hit information. | `PlayerLookAtNode` |
| Dimension Info | `input.context.dimension_info` | Gets the current dimension and basic dimension traits from the active Minecraft world. | `DimensionInfoNode` |
| Current Time | `input.context.current_time` | Gets the current time and weather state from the active Minecraft world. | `CurrentTimeNode` |

## input.numeric（8）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| 整数输入 | `input.numeric.integer` | 允许手动输入整数值的节点 | `IntegerInputNode` |
| 浮点数输入 | `input.numeric.float` | 允许用户手动输入浮点数值 | `FloatInputNode` |
| 整数滑动条 | `input.numeric.integer_slider` | 输出一个可通过滑动条调节的整数值 | `IntegerSliderNode` |
| 浮点数滑动条 | `input.numeric.float_slider` | 输出一个可通过滑动条调节的浮点数。 | `FloatSliderNode` |
| 角度滑动条 | `input.numeric.angle` | 输出一个可通过滑动条调节的角度值，支持度和弧度输出。 | `AngleSliderNode` |
| 圆形角度选择器 | `input.numeric.angle_picker` | 通过圆形表盘选择角度，同时输出度和弧度。 | `CircularAngleNode` |
| Vector Input | `input.numeric.vector_input` | Inputs a 3D vector and outputs vector + X/Y/Z components. | `VectorInputNode` |
| Range Input | `input.numeric.range` | Defines a numeric interval and outputs min/max/span plus a range object. | `RangeInputNode` |

## input.type_selectors（5）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Block Type Selector | `input.type_selectors.block_type_selector` | Searches and selects a Minecraft block type. | `BlockTypeSelectorNode` |
| Entity Type Selector | `input.type_selectors.entity_type_selector` | Searches and selects a Minecraft entity type. | `EntityTypeSelectorNode` |
| Item Type Selector | `input.type_selectors.item_type_selector` | Searches and selects a Minecraft item type. | `ItemTypeSelectorNode` |
| Biome Selector | `input.type_selectors.biome_selector` | Selects a biome id for biome-aware generation workflows. | `BiomeSelectorNode` |
| Block State Selector | `input.type_selectors.block_state_selector` | Builds block-state key/value data from a compact properties string. | `BlockStateSelectorNode` |

## material.basic_assignment（2）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Assign Block Type | `material.basic_assignment.assign_block_type` | Assigns a single block type to every resolved block position | `AssignBlockTypeNode` |
| Block Palette | `material.basic_assignment.block_palette` | Assigns a repeating palette of block types to resolved positions | `BlockPaletteNode` |

## material.block_state（3）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Block State Assign | `material.block_state.block_state_assign` | Assigns explicit block-state properties to placements or voxelized geometry | `BlockStateAssignNode` |
| Auto Orient Blocks | `material.block_state.auto_orient_blocks` | Automatically assigns a facing state from the dominant direction axis | `AutoOrientBlocksNode` |
| Stair Shape | `material.block_state.stair_shape` | Assigns stair facing, half, and inner/outer corner shape states | `StairShapeNode` |

## material.directional_mapping（2）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Top / Side / Bottom Map | `material.directional_mapping.top_side_bottom_map` | Assigns top, side, and bottom block types across a voxelized shape using vertical exposure | `TopSideBottomMapNode` |
| Slope Map | `material.directional_mapping.slope_map` | Assigns flat/slope/steep materials using local height difference per X/Z column. | `SlopeMapNode` |

## material.gradient_mapping（3）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Height Gradient Map | `material.gradient_mapping.height_gradient_map` | Assigns lower, middle, and upper block types across a shape based on relative height | `HeightGradientMapNode` |
| Noise Material | `material.gradient_mapping.noise_material` | Assigns block types across placements or geometry using deterministic 3D noise bands | `NoiseMaterialNode` |
| Gradient Ramp Map | `material.gradient_mapping.gradient_ramp_map` | Assigns block types by height using a custom multi-stop ramp list. | `GradientRampMapNode` |

## material.pattern_mapping（4）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Checker Pattern Map | `material.pattern_mapping.checker_pattern_map` | Assigns alternating block types across a voxelized shape using a checker pattern | `CheckerPatternMapNode` |
| Stripe Pattern Map | `material.pattern_mapping.stripe_pattern_map` | Assigns alternating stripe materials along a selected axis. | `StripePatternMapNode` |
| Brick Pattern Map | `material.pattern_mapping.brick_pattern_map` | Assigns two materials using a staggered brick-like pattern in X/Z. | `BrickPatternMapNode` |
| Grid Pattern Map | `material.pattern_mapping.grid_pattern_map` | Assigns frame/fill materials using a regular X/Z grid interval. | `GridPatternMapNode` |

## material.surface_aging（3）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Weathering | `material.surface_aging.weathering` | Replaces part of a block set with an aged material using a deterministic weathering ratio | `WeatheringNode` |
| Moss Growth | `material.surface_aging.moss_growth` | Applies moss material preferentially to upward-facing/exposed blocks. | `MossGrowthNode` |
| Crack Pattern | `material.surface_aging.crack_pattern` | Adds deterministic crack lines by replacing sparse diagonal bands. | `CrackPatternNode` |

## math.compare（7）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Compare | `math.compare.compare` | Compares two values and outputs equality and ordering relations. | `CompareNode` |
| Equals (==) | `math.compare.equals` | Returns true when A equals B. | `EqualsNode` |
| Not Equals (!=) | `math.compare.not_equals` | Returns true when A does not equal B. | `NotEqualsNode` |
| Less Than (<) | `math.compare.less_than` | Returns true when A is less than B. | `LessThanNode` |
| Less Than or Equal (<=) | `math.compare.less_than_or_equal` | Returns true when A is less than or equal to B. | `LessThanOrEqualNode` |
| Greater Than (>) | `math.compare.greater_than` | Returns true when A is greater than B. | `GreaterThanNode` |
| Greater Than or Equal (>=) | `math.compare.greater_than_or_equal` | Returns true when A is greater than or equal to B. | `GreaterThanOrEqualNode` |

## math.sequence（3）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Range | `math.sequence.range` | Generates a numeric sequence from Start to End using Step. | `MathRangeNode` |
| Data Series | `math.sequence.series` | Generates a series of numbers with constant increment | `DataSeriesNode` |
| Repeat | `math.sequence.repeat` | Repeats a single data item or list multiple times | `RepeatNode` |
 
## math.list（16）
 
| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Create List | `math.list.create_list` | Packs multiple input items into a single list. | `CreateListNode` |
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
| Sequence Range (Legacy) | `math.list.range_legacy` | [Deprecated] Legacy duplicate of Range; use `math.sequence.range`. | `SequenceRangeNode` |
| Set Item | `math.list.set_item` | Sets an item in a list at a specified index | `SetItemNode` |
| Shuffle List | `math.list.shuffle_list` | Randomly reorders elements in a list | `ShuffleListNode` |
| Sort List | `math.list.sort_list` | Sorts elements of a list | `SortListNode` |
| Sub List | `math.list.sub_list` | Gets a portion of a list between start and end indexes. | `SubListNode` |

## math.logic（6）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| If | `math.logic.if` | Chooses between true and false values based on a condition. | `IfNode` |
| Switch | `math.logic.switch` | Selects one of multiple inputs by index, with a default fallback. | `SelectItemNode` |
| AND | `math.logic.and` | Returns true only when both inputs evaluate to true. | `AndNode` |
| OR | `math.logic.or` | Returns true when either input evaluates to true. | `OrNode` |
| NOT | `math.logic.not` | Returns the negated boolean value of the input. | `NotNode` |
| XOR | `math.logic.xor` | Returns true only when exactly one input evaluates to true. | `XorNode` |

## math.random（4）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Random Number | `math.random.random_number` | Generates random numbers within a specified range. | `RandomNumberNode` |
| Noise | `math.random.noise` | Samples coherent noise from a 3D position and seed. | `NoiseNode` |
| Random List Item | `math.random.random_list_item` | Randomly selects one or more items from a list. | `RandomListItemNode` |
| Random Vector | `math.random.random_vector` | Generates random vectors within a specified bounding box. | `RandomVectorNode` |

## math.scalar_math（15）

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
| Clamp | `math.scalar_math.clamp` | Restricts a value to the specified minimum and maximum values. | `ClampNode` |
| Remap | `math.scalar_math.remap` | Maps a value from an input range to an output range. | `RemapNode` |
| Floor | `math.scalar_math.floor` | Rounds a value down to the nearest integer. | `FloorNode` |
| Ceiling | `math.scalar_math.ceiling` | Rounds a value up to the nearest integer. | `CeilingNode` |
| Round | `math.scalar_math.round` | Rounds a value to the nearest integer. | `RoundNode` |

## math.trigonometry（9）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| 正弦函数 (Sin) | `math.trigonometry.sin` | 计算角度的正弦值（输入为弧度） | `SineNode` |
| 余弦函数 (Cos) | `math.trigonometry.cos` | 计算角度的余弦值（输入为弧度） | `CosineNode` |
| 正切函数 (Tan) | `math.trigonometry.tan` | 计算角度的正切值（输入为弧度） | `TangentNode` |
| 角度转弧度 | `math.trigonometry.deg_to_rad` | 将角度从度数转换为弧度 | `DegreesToRadiansNode` |
| 弧度转角度 | `math.trigonometry.rad_to_deg` | 将角度从弧度转换为度数 | `RadiansToDegreesNode` |
| 反正弦函数 (ArcSin) | `math.trigonometry.asin` | 计算输入值的反正弦值（结果以弧度为单位） | `ArcSinNode` |
| 反余弦函数 (ArcCos) | `math.trigonometry.acos` | 计算输入值的反余弦值（结果以弧度为单位） | `ArcCosNode` |
| 反正切函数 (ArcTan) | `math.trigonometry.atan` | 计算输入值的反正切值（结果以弧度为单位） | `ArcTanNode` |
| 圆周率 | `math.trigonometry.pi` | 输出数学常数π的值 | `PiNode` |

## output.debug（4）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| 数据预览 | `output.debug.value_monitor` | 将任意输出连到输入，在面板上查看该输出的数据和类型 | `ValueMonitorNode` |
| 打印到聊天 | `output.debug.print_to_chat` | 将输入数据显示到游戏聊天框 | `PrintToChatNode` |
| 执行计时器 | `output.debug.execution_timer` | 测量连接到此节点的计算分支所花费的时间 | `ExecutionTimerNode` |
| 面板 | `output.debug.data_inspector` | 显示连接到其输入端口的原始数据（文本形式） | `PanelNode` |

## output.execute（15）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Apply Changes | `output.execute.apply_changes` | Applies explicit placements or voxelized geometry to the world. | `ApplyChangesNode` |
| Clear Preview | `output.execute.clear_preview` | Clears all active previews in the current world | `ClearAllPreviewsNode` |
| Bake Geometry To Blocks | `output.execute.bake_geometry_to_blocks` | Bakes any supported geometry into Minecraft block coordinates for final execution | `GeometryToBlocksNode` |
| Undo Last Bake | `output.execute.undo_last_bake` | Reverts the most recent recorded bake or apply-changes operation | `UndoLastBakeNode` |
| Bake Surface Strip To Blocks | `output.execute.bake_surface_strip_to_blocks` | Bakes a surface strip into block coordinates for final execution | `SurfaceStripToBlocksNode` |
| Bake Box To Blocks | `output.execute.bake_box_to_blocks` | [Deprecated] Use Bake Geometry To Blocks (`output.execute.bake_geometry_to_blocks`) for new graphs. | `BoxGeometryVoxelizerNode` |
| Bake Sphere To Blocks | `output.execute.bake_sphere_to_blocks` | [Deprecated] Use Bake Geometry To Blocks (`output.execute.bake_geometry_to_blocks`) for new graphs. | `SphereGeometryVoxelizerNode` |
| Bake Cylinder To Blocks | `output.execute.bake_cylinder_to_blocks` | [Deprecated] Use Bake Geometry To Blocks (`output.execute.bake_geometry_to_blocks`) for new graphs. | `CylinderGeometryVoxelizerNode` |
| Bake Cone To Blocks | `output.execute.bake_cone_to_blocks` | [Deprecated] Use Bake Geometry To Blocks (`output.execute.bake_geometry_to_blocks`) for new graphs. | `ConeGeometryVoxelizerNode` |
| Bake Ellipsoid To Blocks | `output.execute.bake_ellipsoid_to_blocks` | [Deprecated] Use Bake Geometry To Blocks (`output.execute.bake_geometry_to_blocks`) for new graphs. | `EllipsoidGeometryVoxelizerNode` |
| Bake Prism To Blocks | `output.execute.bake_prism_to_blocks` | [Deprecated] Use Bake Geometry To Blocks (`output.execute.bake_geometry_to_blocks`) for new graphs. | `PrismGeometryVoxelizerNode` |
| Bake Octahedron To Blocks | `output.execute.bake_octahedron_to_blocks` | [Deprecated] Use Bake Geometry To Blocks (`output.execute.bake_geometry_to_blocks`) for new graphs. | `OctahedronGeometryVoxelizerNode` |
| Bake Tetrahedron To Blocks | `output.execute.bake_tetrahedron_to_blocks` | [Deprecated] Use Bake Geometry To Blocks (`output.execute.bake_geometry_to_blocks`) for new graphs. | `TetrahedronGeometryVoxelizerNode` |
| Bake Torus To Blocks | `output.execute.bake_torus_to_blocks` | [Deprecated] Use Bake Geometry To Blocks (`output.execute.bake_geometry_to_blocks`) for new graphs. | `TorusGeometryVoxelizerNode` |
| Merge Block Placements | `output.execute.merge_block_placements` | Concatenates multiple block placement lists into one execution-ready placement list | `MergeBlockPlacementsNode` |

## output.export（3）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Export Schematic | `output.export.export_schematic` | Exports placements to a NodeCraft NBT structure file | `ExportSchematicNode` |
| Export Litematic | `output.export.export_litematic` | Exports placements to a single-region Litematic file | `ExportLitematicNode` |
| Export WorldEdit | `output.export.export_worldedit` | Exports placements to a Sponge schematic file for WorldEdit | `ExportWorldEditNode` |

## output.preview（12）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Geometry Viewer | `output.preview.geometry_viewer` | Previews geometry visually without committing changes to the world. | `GeometryViewerNode` |
| Preview Blocks | `output.preview.preview_blocks` | Previews block coordinates or block lists as temporary ghost blocks. | `PreviewBlocksNode` |
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

## pattern.grid（2）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| 网格阵列 | `pattern.grid.grid_array` | 在平面或三维网格上重复坐标列表 | `GridArrayNode` |
| Facade Grid | `pattern.grid.facade_grid` | Generates facade cell centers and boundaries on a box face | `FacadeGridNode` |

## pattern.linear（2）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| 线性阵列 | `pattern.linear.linear_array` | 将坐标列表沿直线方向重复排列 | `LinearArrayNode` |
| Along Path | `pattern.linear.along_path` | Repeats a block pattern at each resolved path point from a line, polyline, curve, or point list | `AlongPathNode` |

## pattern.radial（1）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| 极坐标阵列 | `pattern.radial.polar_array` | 将坐标列表绕中心点重复旋转排列 | `PolarArrayNode` |

## pattern.surface_volume_distribution（4）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| 区域填充 | `pattern.surface_volume_distribution.populate_region` | 在指定区域内随机或均匀生成坐标列表 | `PopulateRegionNode` |
| Sample Sphere Surface | `pattern.surface_volume_distribution.sample_surface` | Samples points and normals on a sphere surface for scattering and growth workflows | `SampleSphereSurfaceNode` |
| Scatter On Sphere Surface | `pattern.surface_volume_distribution.surface_scatter` | Scatters points on a sphere surface and outputs matching normals and optional snapped block coordinates | `ScatterOnSphereSurfaceNode` |
| Poisson Disk On Plane | `pattern.surface_volume_distribution.poisson_disk_plane` | Samples points on a plane inside a UV rectangle with minimum separation using rejection sampling | `PoissonDiskOnPlaneNode` |

## reference.frames（2）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Face Center Frame | `reference.frames.frame_from_face` | Builds a local frame at the center of a box face using the face plane and boundary directions | `FaceCenterFrameNode` |
| Sphere Surface Frame | `reference.frames.frame_along_surface` | Builds a local tangent frame on a sphere using the nearest surface point and outward normal | `SphereSurfaceFrameNode` |

## reference.planes（5）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| World Plane | `reference.planes.world_plane` | Creates a standard XY, YZ, or XZ world plane with a configurable origin | `PlaneSelectorNode` |
| Construct Plane | `reference.planes.construct_plane` | Constructs a plane from an origin point and a normal vector | `ConstructPlaneNode` |
| Construct Plane From Points | `reference.planes.plane_from_points` | Constructs a plane from three non-collinear points | `ConstructPlaneFromPointsNode` |
| Distance Point To Plane | `reference.planes.distance_point_to_plane` | Measures the absolute and signed distance from a geometric point to a plane | `DistancePointToPlaneNode` |
| Box Face To Plane | `reference.planes.block_face_plane` | Explicitly converts a box face into its supporting plane and related face frame data | `BoxFaceToPlaneNode` |

## reference.points（16）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| 坐标输入 | `reference.points.point_from_coordinates` | 输入一个整数坐标，并同时输出 Coordinate / Block Pos / X / Y / Z | `CoordinateInputNode` |
| Block To Point | `reference.points.point_from_block` | Explicitly converts a block coordinate into a geometric point, with optional block-center offset | `BlockToPointNode` |
| Point Along Vector | `reference.points.point_along_vector` | Creates a new point by moving a start point along a direction vector by a distance | `PointAlongVectorNode` |
| Block To Vector | `reference.points.block_to_vector` | Explicitly converts a block coordinate into a Vector3d position, with optional block-center offset. | `BlockToVectorNode` |
| 分解坐标 | `reference.points.deconstruct_point` | 将坐标分解为X、Y、Z分量 | `DeconstructCoordinateNode` |
| Mid Point | `reference.points.mid_point` | Computes the midpoint between two input points | `MidpointNode` |
| Distance Between Points | `reference.points.distance_between_points` | Computes the distance between two input points | `DistanceNode` |
| 最近点 | `reference.points.closest_point` | 在坐标列表中找到距离指定点最近的点 | `ClosestPointNode` |
| Point List Center | `reference.points.point_list_center` | Calculates the average geometric center of a point list | `PointListCenterNode` |
| Point List Bounds | `reference.points.point_list_bounds` | Calculates an axis-aligned bounding box from a list of geometric points | `PointListBoundsNode` |
| Get Box Corner | `reference.points.get_box_corner` | Gets a single corner from box geometry by index | `GetBoxCornerNode` |
| Get Box Face | `reference.points.get_box_face` | Gets a single face from box geometry by semantic name or index | `GetBoxFaceNode` |
| Get Face Edge | `reference.points.get_face_edge` | Gets a single edge from a face by index | `GetFaceEdgeNode` |
| Deconstruct Box Face | `reference.points.deconstruct_face` | Extracts corners, edges, plane, center, and normal from a box face | `DeconstructBoxFaceNode` |
| Deconstruct Face Edge | `reference.points.deconstruct_edge` | Extracts endpoints, midpoint, direction, vector, and length from a face edge | `DeconstructFaceEdgeNode` |
| Project Point To Polyline | `reference.points.project_to_polyline` | Projects a point onto the closest location on a polyline or line segment | `ProjectPointToPolylineNode` |

## reference.vectors（11）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| 向量输入 | `reference.vectors.vector` | 输入一个三维向量，并同时输出 X / Y / Z 分量 | `VectorInputNode` |
| 解构向量 | `reference.vectors.deconstruct_vector` | 输出向量的X、Y、Z分量 | `DeconstructVectorNode` |
| 向量归一化 | `reference.vectors.normalize_vector` | 将向量归一化为单位长度 | `NormalizeVectorNode` |
| 叉积 | `reference.vectors.cross_product` | 计算两个向量的叉积（A × B） | `CrossProductNode` |
| 点积 | `reference.vectors.dot_product` | 计算两个向量的点积（A · B） | `DotProductNode` |
| 向量长度 | `reference.vectors.vector_length` | 计算向量的长度（模长） | `VectorLengthNode` |
| 向量加法 (+) | `reference.vectors.vector_addition` | 计算两个向量的和，输出A + B | `VectorAdditionNode` |
| 向量减法 (-) | `reference.vectors.vector_subtraction` | 计算两个向量的差，输出A - B | `VectorSubtractionNode` |
| 向量标量乘法 | `reference.vectors.vector_scalar_multiply` | 向量与标量相乘 (Vector * Scalar) | `VectorScalarMultiplyNode` |
| 向量标量除法 | `reference.vectors.vector_scalar_divide` | 向量除以标量 (Vector / Scalar) | `VectorScalarDivideNode` |
| Angle Between Vectors | `reference.vectors.angle_between` | Angle between two vectors in radians and degrees; optional reference vector yields a signed angle | `AngleBetweenVectorsNode` |

## transform.basic_transforms（9）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Offset Coordinate | `transform.basic_transforms.move_point` | Offsets a single block coordinate by integer X, Y, Z amounts | `OffsetCoordinateNode` |
| Offset Coordinates | `transform.basic_transforms.move_points` | 对坐标列表中的每个坐标应用偏移量 | `OffsetCoordinatesNode` |
| Rotate Coordinates | `transform.basic_transforms.rotate_points` | 绕指定轴和中心点旋转坐标列表 | `RotateCoordinatesNode` |
| Scale Coordinates | `transform.basic_transforms.scale_points` | 以指定中心点为基准缩放坐标列表 | `ScaleCoordinatesNode` |
| Mirror Coordinates | `transform.basic_transforms.mirror_points` | 沿指定平面镜像坐标列表 | `MirrorCoordinatesNode` |
| Offset Box Face | `transform.basic_transforms.offset_face` | Offsets a box face along its normal without modifying the source box geometry | `OffsetBoxFaceNode` |
| Inset Box Face | `transform.basic_transforms.inset_face` | Creates an inset or outset reference face boundary from a box face without modifying the source box | `InsetBoxFaceNode` |
| Mirror Geometry About Plane | `transform.basic_transforms.mirror_geometry_plane` | Mirrors analytic geometry about a plane (recursive for composites and boolean geometry nodes) | `MirrorGeometryAboutPlaneNode` |
| Mirror Vector List About Plane | `transform.basic_transforms.mirror_vector_list_plane` | Mirrors each point in a list about a plane and outputs Vector3d positions | `MirrorVectorListAboutPlaneNode` |

## transform.deformations（1）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Twist Point List | `transform.deformations.twist` | Twists a point list around an axis by distributing rotation along a specified axial length | `TwistPointListNode` |

## transform.orientation（2）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Project Point To Plane | `transform.orientation.project_to_plane` | Projects a geometric point onto a plane and reports the projection distance | `ProjectPointToPlaneNode` |
| 旋转向量 | `transform.orientation.rotate_vector` | 绕指定轴旋转向量 | `RotateVectorNode` |

## utilities.assist（4）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| 中继节点 | `utilities.assist.reroute` | 用于整理连线的中继节点，仅透传输入到输出 | `RerouteNode` |
| 分线节点 | `utilities.assist.signal_fork` | 将一路输入透传到两路输出，便于连线分流 | `SignalForkNode` |
| 标签中继 | `utilities.assist.tag_relay` | 用于标注语义的中继节点，输入输出保持透传 | `TagRelayNode` |
| 汇线节点 | `utilities.assist.signal_merge` | 将两路输入按优先级汇聚为一路输出 | `SignalMergeNode` |

## utilities.fileio（1）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Read Image | `utilities.fileio.read_image` | Reads a local image file and outputs dimensions, colors, and grayscale samples | `ReadImageNode` |

## utilities.morphology（1）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Block List Morphology | `utilities.morphology.block_list_morphology` | Dilates or erodes a block list using 6- or 26-neighbor morphology iterations (Connectivity property) | `BlockListMorphologyNode` |

## utilities.organization（3）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| 注释节点 | `utilities.organization.comment` | 在画布上添加文本注释 | `CommentNode` |
| 节点分组 | `utilities.organization.group` | 将选中的节点打包成一个可视化组 | `GroupNode` |
| 节点对齐 | `utilities.organization.align_nodes` | 对齐选中的节点 | `AlignNodesNode` |

## world.query（7）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Get Light Level | `world.query.get_light_level` | Gets the combined, sky, and block light values for a block position | `GetLightLevelNode` |
| Get Fluid Level | `world.query.get_fluid_level` | Gets the fluid state, type, and level for a block position | `GetFluidLevelNode` |
| Is Grid Point | `world.query.is_grid_point` | Checks whether a geometric point already lies on the block grid without snapping | `IsGridPointNode` |
| Filter Grid Points | `world.query.filter_grid_points` | Splits a point list into grid-aligned points and off-grid points without snapping | `FilterGridPointsNode` |
| 点在区域内 | `world.query.is_point_in_region` | 检查点是否在指定区域内 | `IsPointInRegionNode` |
| Get Entities In Region | `world.query.get_entities_in_region` | Gets entities inside a region with optional filtering | `GetEntitiesInRegionNode` |
| 获取实体 | `world.query.get_entity` | 根据实体ID或选择器获取实体信息 | `GetEntityNode` |

## world.read（9）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| 获取方块 | `world.read.get_block` | 获取指定坐标的方块信息 | `GetBlockNode` |
| 获取区域内方块 | `world.read.get_blocks_in_region` | 获取区域内所有方块的列表 | `GetBlocksInRegionNode` |
| 查找方块 | `world.read.find_blocks` | 在区域内按方块类型查找坐标列表 | `FindBlocksNode` |
| Get Biome | `world.read.get_biome` | Gets the biome registry id and basic climate data for a block position | `GetBiomeNode` |
| Biome At Player | `world.read.biome_at_player` | Gets the biome at the player's current position | `BiomeAtPlayerNode` |
| 获取区域内点 | `world.read.get_points_in_region` | 获取区域内的所有坐标点 | `GetPointsInRegionNode` |
| Get Heightmap | `world.read.get_heightmap` | Reads the top Y value for each X/Z column inside a region | `GetHeightmapNode` |
| Get Surface Blocks | `world.read.get_surface_blocks` | Gets the top visible block for each X/Z column inside a region | `GetSurfaceBlocksNode` |
| Read Sign Text | `world.read.read_sign_text` | Reads text from a sign block entity | `ReadSignTextNode` |

## world.selection（8）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| 选定方块 | `world.selection.selected_block` | 获取方块信息，支持交互拾取或坐标输入 | `SelectedBlockNode` |
| Selected Region | `world.selection.selected_region` | Gets the player's selected region defined by two corner points. | `SelectedRegionNode` |
| Snap Point To Block | `world.selection.snap_point_to_block` | Explicitly snaps a geometric point onto the block grid using floor, nearest, or ceil | `SnapPointToBlockNode` |
| Snap Vector To Block | `world.selection.snap_vector_to_block` | Converts a Vector3d position into a block coordinate using floor, round, or ceil snapping. | `SnapVectorToBlockNode` |
| Snap Point List To Blocks | `world.selection.snap_points_to_blocks` | Snaps a point list onto the block grid using an explicit snap mode | `SnapPointListToBlocksNode` |
| Point To Block If Grid | `world.selection.point_to_block_if_grid` | Strict conversion: outputs a block coordinate only when the point is already grid-aligned | `PointToBlockIfGridNode` |
| Selected Block Sequence | `world.selection.selected_block_sequence` | Collects multiple picked blocks in click order and outputs an ordered block sequence | `SelectedBlockSequenceNode` |
| Selected Entity | `world.selection.selected_entity` | Gets information about the entity selected by the player. | `SelectedEntityNode` |

## world.write（13）

| 节点名称 | 节点 ID | 说明 | 类名 |
|---|---|---|---|
| Set Block | `world.write.set_block` | Places one block at one block position | `SetBlockNode` |
| 设置方块 | `world.write.set_blocks` | 在坐标列表上批量放置方块 | `SetBlocksNode` |
| 填充区域 | `world.write.fill_region` | 用指定方块填充区域 | `FillRegionNode` |
| 替换方块 | `world.write.replace_blocks` | 在区域或坐标列表中替换指定方块 | `ReplaceBlocksNode` |
| 复制区域 | `world.write.clone_region` | 复制区域到另一个位置 | `CloneRegionNode` |
| Clear Blocks | `world.write.clear_region` | Clears blocks at explicit coordinates by replacing them with air | `RemoveBlocksNode` |
| Apply Redstone Power | `world.write.apply_redstone_power` | Places a temporary redstone power source next to a target block | `ApplyRedstonePowerNode` |
| Execute Command | `world.write.execute_command` | Executes a Minecraft command on the server | `ExecuteCommandNode` |
| Simulate Right Click | `world.write.simulate_right_click` | Simulates a server-side right click on a block | `SimulateRightClickNode` |
| Spawn Entity | `world.write.spawn_entity` | Spawns an entity into the world at a given position | `SpawnEntityNode` |
| Write Sign Text | `world.write.write_sign_text` | Writes text to a sign block entity | `WriteSignTextNode` |
| 传送实体 | `world.write.entity_teleport` | 传送实体 | `EntityTeleportNode` |
| 移除实体 | `world.write.remove_entities` | 移除实体 | `RemoveEntitiesNode` |

## 文档生成说明

- 本文档由源码中的 `@NodeInfo` 元数据自动汇总生成。
- 若某类节点缺少 `@NodeInfo`，会按包路径推断分类 ID，并按类名生成默认展示名。
- 需要更完整的中文说明时，可在对应节点类的 `@NodeInfo.description` 中补充。
- Bake 节点弃用/移除计划：`docs/bake-node-deprecation-plan.md`。
