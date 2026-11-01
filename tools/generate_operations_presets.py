#!/usr/bin/env python3
"""Inject teaching operations presets + modern_window fix into graph_presets.json."""
from __future__ import annotations

import json
from copy import deepcopy
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRAPH = ROOT / "src/main/resources/nodecraft/graph_presets.json"

COL = 320.0
ROW = 180.0


def n(ref: str, type_id: str, col: float, row: float, state: dict | None = None) -> dict:
    node = {"ref": ref, "typeId": type_id, "x": col * COL, "y": 40.0 + row * ROW}
    if state is not None:
        node["state"] = state
    return node


def c(fr: str, fp: str, tr: str, tp: str) -> dict:
    return {"fromRef": fr, "fromPort": fp, "toRef": tr, "toPort": tp}


def float_n(ref: str, value: float, col: float, row: float) -> dict:
    return n(ref, "input.numeric.float", col, row, {"value": float(value)})


def int_n(ref: str, value: int, col: float, row: float) -> dict:
    return n(ref, "input.numeric.integer", col, row, {"value": int(value)})


def vector_n(ref: str, x: float, y: float, z: float, col: float, row: float) -> dict:
    return n(ref, "reference.vectors.vector", col, row, {"x": float(x), "y": float(y), "z": float(z)})


def construct_point(prefix: str, x: float, y: float, z: float, col: float, row: float):
    """Three floats + construct_point. Returns (nodes, connections, point_ref)."""
    nodes = [
        float_n(f"{prefix}_x", x, col, row),
        float_n(f"{prefix}_y", y, col, row + 1),
        float_n(f"{prefix}_z", z, col, row + 2),
        n(prefix, "reference.points.construct_point", col + 1, row + 1),
    ]
    conns = [
        c(f"{prefix}_x", "output_value", prefix, "input_x"),
        c(f"{prefix}_y", "output_value", prefix, "input_y"),
        c(f"{prefix}_z", "output_value", prefix, "input_z"),
    ]
    return nodes, conns, prefix


def player_chain(geo_ref: str, block: str = "minecraft:stone", sink_col: float = 4.0):
    """Canonical local→player Move + preview/voxel/material sinks."""
    nodes = [
        n("player_pos", "input.context.player_position", 0, 0),
        n(
            "material_block_type",
            "input.type_selectors.block_type_selector",
            0,
            1,
            {"selectedBlock": block},
        ),
        n("move_to_pos_point_deconstruct", "reference.points.deconstruct_point", sink_col - 2, 0),
        n("move_to_pos_point_as_vector", "reference.vectors.construct_vector", sink_col - 1, 0.5),
        n("move_to_pos", "transform.basic_transforms.move_geometry", sink_col, 0.5),
        n("preview_geometry", "output.preview.preview_geometry", sink_col + 1, 0),
        n("voxelize", "geometry.voxel.voxelize_geometry", sink_col + 1, 1),
        n("material", "material.basic_assignment.assign_block_type", sink_col + 2, 0.5),
        n("preview_blocks", "output.preview.preview_blocks", sink_col + 3, 0.5),
    ]
    conns = [
        c("player_pos", "output_position", "move_to_pos_point_deconstruct", "input_point"),
        c("move_to_pos_point_deconstruct", "output_x", "move_to_pos_point_as_vector", "input_x"),
        c("move_to_pos_point_deconstruct", "output_y", "move_to_pos_point_as_vector", "input_y"),
        c("move_to_pos_point_deconstruct", "output_z", "move_to_pos_point_as_vector", "input_z"),
        c("move_to_pos_point_as_vector", "output_vector", "move_to_pos", "input_translation"),
        c(geo_ref, "output_geometry", "move_to_pos", "input_geometry"),
        c("move_to_pos", "output_geometry", "preview_geometry", "input_geometry"),
        c("move_to_pos", "output_geometry", "voxelize", "input_geometry"),
        c("voxelize", "output_blocks", "material", "input_coordinates"),
        c("material_block_type", "output_block_id", "material", "input_block_type"),
        c("material", "output_placements", "preview_blocks", "input_block_placements"),
    ]
    return nodes, conns


def meta(difficulty: str, tags: list[str], notes: str, tips: list[str] | None = None) -> dict:
    out = {
        "difficulty": difficulty,
        "tags": tags,
        "learningNotes": notes,
    }
    if tips:
        out["tips"] = tips
    return out


def preset(pid: str, name: str, desc: str, nodes: list, conns: list, **metadata) -> dict:
    p = {
        "id": pid,
        "displayName": name,
        "description": desc,
        "kind": "composite",
        "nodes": nodes,
        "connections": conns,
    }
    p.update(metadata)
    return p


def simple_primitive(pid: str, name: str, type_id: str, state: dict, block: str, notes: str, tags: list[str]):
    body = n("shape", type_id, 1, 2, state)
    sinks, sc = player_chain("shape", block, sink_col=3)
    return preset(
        pid,
        name,
        f"Local-space {name} at origin → Move to Player → Preview Geometry / Voxelize → Assign Block → Preview Blocks.",
        [body] + sinks,
        sc,
        **meta("beginner", tags, notes, ["Geometry is authored at the origin; Player Position only drives the final Move."]),
    )


def build_operations() -> list[dict]:
    presets: list[dict] = []

    # --- Primitives ---
    presets.append(
        simple_primitive(
            "operations.primitive.torus",
            "Torus",
            "geometry.primitives.torus",
            {
                "centerX": 0.0,
                "centerY": 3.0,
                "centerZ": 0.0,
                "axisX": 0.0,
                "axisY": 1.0,
                "axisZ": 0.0,
                "majorRadius": 4.0,
                "minorRadius": 1.2,
            },
            "minecraft:smooth_stone",
            "Ring torus: majorRadius must exceed minorRadius. Axis is the torus normal.",
            ["primitive", "torus", "geometry"],
        )
    )
    presets.append(
        simple_primitive(
            "operations.primitive.cone",
            "Cone",
            "geometry.primitives.cone",
            {
                "baseX": 0.0,
                "baseY": 0.0,
                "baseZ": 0.0,
                "apexX": 0.0,
                "apexY": 6.0,
                "apexZ": 0.0,
                "radius": 3.0,
            },
            "minecraft:terracotta",
            "Cone from base center + apex + radius. Axis is the base→apex segment.",
            ["primitive", "cone", "geometry"],
        )
    )
    presets.append(
        simple_primitive(
            "operations.primitive.capsule",
            "Capsule",
            "geometry.primitives.capsule",
            {
                "startX": 0.0,
                "startY": 0.0,
                "startZ": 0.0,
                "endX": 0.0,
                "endY": 6.0,
                "endZ": 0.0,
                "radius": 1.5,
            },
            "minecraft:orange_concrete",
            "Capsule = cylinder + hemispherical caps on the start/end axis.",
            ["primitive", "capsule", "geometry"],
        )
    )
    presets.append(
        simple_primitive(
            "operations.primitive.ellipsoid",
            "Ellipsoid",
            "geometry.primitives.ellipsoid",
            {
                "centerX": 0.0,
                "centerY": 3.0,
                "centerZ": 0.0,
                "radiusX": 4.0,
                "radiusY": 2.0,
                "radiusZ": 3.0,
            },
            "minecraft:yellow_concrete",
            "Stretched sphere with independent X/Y/Z radii.",
            ["primitive", "ellipsoid", "geometry"],
        )
    )
    presets.append(
        simple_primitive(
            "operations.primitive.square_pyramid",
            "Square Pyramid",
            "geometry.primitives.square_pyramid",
            {
                "centerX": 0.0,
                "centerY": 0.0,
                "centerZ": 0.0,
                "baseSize": 6.0,
                "height": 5.0,
            },
            "minecraft:sandstone",
            "Pyramid with square base centered on the construction plane; height along +Y.",
            ["primitive", "pyramid", "geometry"],
        )
    )
    presets.append(
        simple_primitive(
            "operations.primitive.hemisphere",
            "Hemisphere",
            "geometry.primitives.hemisphere",
            {
                "centerX": 0.0,
                "centerY": 0.0,
                "centerZ": 0.0,
                "axisX": 0.0,
                "axisY": 1.0,
                "axisZ": 0.0,
                "radius": 4.0,
            },
            "minecraft:light_blue_concrete",
            "Half-sphere. Axis points toward the curved side (flat face opposite the axis).",
            ["primitive", "hemisphere", "geometry"],
        )
    )

    # Frustum requires two points
    base_nodes, base_conns, base_ref = construct_point("base_center", 0.0, 0.0, 0.0, 0, 2)
    top_nodes, top_conns, top_ref = construct_point("top_center", 0.0, 6.0, 0.0, 0, 5)
    frustum = n(
        "frustum",
        "geometry.primitives.frustum_cone",
        2,
        4,
        {"baseRadius": 3.5, "topRadius": 1.5},
    )
    sinks, sc = player_chain("frustum", "minecraft:bricks", sink_col=4)
    presets.append(
        preset(
            "operations.primitive.frustum",
            "Frustum Cone",
            "Truncated cone from base/top centers + radii → Move to Player → voxel preview chain.",
            base_nodes + top_nodes + [frustum] + sinks,
            base_conns
            + top_conns
            + [
                c(base_ref, "output_point", "frustum", "input_base_center"),
                c(top_ref, "output_point", "frustum", "input_top_center"),
            ]
            + sc,
            **meta(
                "beginner",
                ["primitive", "frustum", "construct_point"],
                "FrustumCone requires connected base/top centers; Construct Point teaches XYZ → Point wiring.",
            ),
        )
    )

    # Platonic pair (tetra + octa) combined
    tetra = n(
        "tetra",
        "geometry.primitives.tetrahedron",
        1,
        2,
        {
            "centerX": -3.0,
            "centerY": 2.0,
            "centerZ": 0.0,
            "defaultSize": 3.0,
            "rotationXDeg": 0.0,
            "rotationYDeg": 0.0,
            "rotationZDeg": 0.0,
        },
    )
    octa = n(
        "octa",
        "geometry.primitives.octahedron",
        1,
        3,
        {
            "centerX": 3.0,
            "centerY": 2.0,
            "centerZ": 0.0,
            "defaultSize": 3.0,
            "rotationXDeg": 0.0,
            "rotationYDeg": 20.0,
            "rotationZDeg": 0.0,
        },
    )
    combine_platonics = n("combine", "geometry.combine.geometry", 2, 2.5, {"inputCount": 2})
    sinks, sc = player_chain("combine", "minecraft:amethyst_block", sink_col=4)
    presets.append(
        preset(
            "operations.primitive.platonic_pair",
            "Platonic Pair (Combine)",
            "Tetrahedron + Octahedron → Combine Geometry → Move to Player → voxel preview.",
            [tetra, octa, combine_platonics] + sinks,
            [
                c("tetra", "output_geometry", "combine", "input_geometry_0"),
                c("octa", "output_geometry", "combine", "input_geometry_1"),
            ]
            + sc,
            **meta(
                "beginner",
                ["primitive", "platonic", "combine"],
                "Combine Geometry merges multiple local solids before the shared Move / Voxelize chain.",
                ["Give repeated primitives distinct state so they do not overlap."],
            ),
        )
    )

    # --- Solids / loft ---
    # Extrude rectangle
    profile = n("profile", "geometry.profiles.rectangle_profile", 1, 2, {"width": 4.0, "height": 3.0})
    direction = vector_n("extrude_dir", 0.0, 5.0, 0.0, 1, 3)
    extrude = n("extrude", "geometry.solids.extrude", 2, 2.5)
    sinks, sc = player_chain("extrude", "minecraft:oak_planks", sink_col=4)
    presets.append(
        preset(
            "operations.solid.extrude_profile",
            "Extrude Profile",
            "Rectangle Profile → Extrude (Prism) → Move to Player → voxel preview. Extrude outputs solid PrismGeometry.",
            [profile, direction, extrude] + sinks,
            [
                c("profile", "output_profile", "extrude", "input_profile"),
                c("extrude_dir", "output_vector", "extrude", "input_direction"),
            ]
            + sc,
            **meta(
                "beginner",
                ["solid", "extrude", "profile"],
                "Extrude is solid (PrismGeometry) and voxelizes directly — no surface lattice conversion needed.",
            ),
        )
    )

    # Loft two circles
    bot_nodes, bot_conns, bot_ref = construct_point("bottom_center", 0.0, 0.0, 0.0, 0, 2)
    topc_nodes, topc_conns, topc_ref = construct_point("top_center", 0.0, 8.0, 0.0, 0, 5)
    bot_profile = n("bottom_profile", "geometry.profiles.circle_profile", 2, 3, {"radius": 3.0, "segments": 24})
    top_profile = n("top_profile", "geometry.profiles.circle_profile", 2, 6, {"radius": 1.5, "segments": 24})
    loft = n("loft", "geometry.solids.loft", 3, 4.5, {"resampleCount": 24, "matchSeamMode": "index"})
    lattice = n(
        "lattice",
        "geometry.solids.surface_strip_to_lattice",
        4,
        4.5,
        {"mode": "LATTICE", "longitudinalSteps": 6, "radius": 0.4},
    )
    sinks, sc = player_chain("lattice", "minecraft:copper_block", sink_col=6)
    presets.append(
        preset(
            "operations.solid.loft_profiles",
            "Loft Two Profiles",
            "Two Circle Profiles at different heights → Loft → Surface Strip to Lattice → Move → voxel preview.",
            bot_nodes + topc_nodes + [bot_profile, top_profile, loft, lattice] + sinks,
            bot_conns
            + topc_conns
            + [
                c(bot_ref, "output_point", "bottom_profile", "input_center"),
                c(topc_ref, "output_point", "top_profile", "input_center"),
                c("bottom_profile", "output_profile", "loft", "input_source_profile"),
                c("top_profile", "output_profile", "loft", "input_target_profile"),
                c("loft", "output_side_surface", "lattice", "input_surface_strip"),
            ]
            + sc,
            **meta(
                "intermediate",
                ["solid", "loft", "surface_strip", "lattice"],
                "Loft emits a SurfaceStrip. Route through Surface Strip to Lattice before Voxelize.",
                ["Match segment counts (or set resampleCount) so loft seams align."],
            ),
        )
    )

    # Revolve
    rev_profile = n("profile", "geometry.profiles.rectangle_profile", 2, 3, {"width": 1.5, "height": 3.0})
    # offset profile center away from Y axis
    pcent_nodes, pcent_conns, pcent_ref = construct_point("profile_center", 3.0, 2.0, 0.0, 0, 2)
    axis_nodes, axis_conns, axis_ref = construct_point("axis_origin", 0.0, 0.0, 0.0, 0, 5)
    # Keep clear of construct_point at col=1,row=6 and float stack at col=0
    axis_dir = vector_n("axis_dir", 0.0, 1.0, 0.0, 2, 5)
    angle = float_n("angle", 360.0, 2, 6)
    steps = int_n("steps", 24, 2, 7)
    revolve = n("revolve", "geometry.solids.revolve", 3, 4)
    lattice = n(
        "lattice",
        "geometry.solids.surface_strip_to_lattice",
        4,
        4,
        {"mode": "LATTICE", "longitudinalSteps": 4, "radius": 0.35},
    )
    sinks, sc = player_chain("lattice", "minecraft:red_nether_bricks", sink_col=6)
    presets.append(
        preset(
            "operations.solid.revolve_profile",
            "Revolve Profile",
            "Offset Rectangle Profile → Revolve around Y → Surface Strip to Lattice → Move → voxel preview.",
            pcent_nodes
            + axis_nodes
            + [rev_profile, axis_dir, angle, steps, revolve, lattice]
            + sinks,
            pcent_conns
            + axis_conns
            + [
                c(pcent_ref, "output_point", "profile", "input_center"),
                c("profile", "output_profile", "revolve", "input_profile"),
                c(axis_ref, "output_point", "revolve", "input_axis_origin"),
                c("axis_dir", "output_vector", "revolve", "input_axis_direction"),
                c("angle", "output_value", "revolve", "input_angle_degrees"),
                c("steps", "output_value", "revolve", "input_steps"),
                c("revolve", "output_surface_strip", "lattice", "input_surface_strip"),
            ]
            + sc,
            **meta(
                "intermediate",
                ["solid", "revolve", "surface_strip"],
                "Keep the profile away from the axis. Axis Origin + Direction is the teaching-friendly axis input.",
            ),
        )
    )

    # Sweep along helix
    sw_profile = n("profile", "geometry.profiles.circle_profile", 1, 2, {"radius": 0.8, "segments": 16})
    helix = n(
        "helix",
        "geometry.curves.helix",
        1,
        3,
        {
            "centerX": 0.0,
            "centerY": 0.0,
            "centerZ": 0.0,
            "axisX": 0.0,
            "axisY": 1.0,
            "axisZ": 0.0,
            "defaultRadius": 3.0,
            "defaultPitch": 2.5,
            "defaultTurns": 2.5,
            "defaultSegmentsPerTurn": 20,
            "defaultStartAngle": 0.0,
        },
    )
    sweep = n("sweep", "geometry.solids.sweep", 2, 2.5, {"orientToPath": True, "closeProfile": True})
    lattice = n(
        "lattice",
        "geometry.solids.surface_strip_to_lattice",
        3,
        2.5,
        {"mode": "LATTICE", "longitudinalSteps": 3, "radius": 0.3},
    )
    sinks, sc = player_chain("lattice", "minecraft:gold_block", sink_col=5)
    presets.append(
        preset(
            "operations.solid.sweep_helix",
            "Sweep Along Helix",
            "Circle Profile + Helix Path → Sweep → Surface Strip to Lattice → Move → voxel preview.",
            [sw_profile, helix, sweep, lattice] + sinks,
            [
                c("profile", "output_profile", "sweep", "input_profile"),
                c("helix", "output_path", "sweep", "input_path"),
                c("sweep", "output_surface_strip", "lattice", "input_surface_strip"),
            ]
            + sc,
            **meta(
                "intermediate",
                ["solid", "sweep", "helix", "curve"],
                "Sweep follows a Path. Helix is a compact teaching path for spiral solids.",
            ),
        )
    )

    # --- Transforms ---
    box = n(
        "box",
        "geometry.primitives.box_from_corner_size",
        1,
        2,
        {"cornerX": 0.0, "cornerY": 0.0, "cornerZ": 0.0, "sizeX": 3.0, "sizeY": 2.0, "sizeZ": 2.0},
    )
    offset = n("offset_move", "transform.basic_transforms.move_geometry", 2, 2, {"x": 5.0, "y": 0.0, "z": 0.0})
    sinks, sc = player_chain("offset_move", "minecraft:white_concrete", sink_col=4)
    # player_chain wires geo_ref→move_to_pos; here geo is offset_move
    presets.append(
        preset(
            "operations.transform.move_offset",
            "Move Offset",
            "Box → Move Geometry (local +X offset via state) → Move to Player → voxel preview.",
            [box, offset] + sinks,
            [c("box", "output_geometry", "offset_move", "input_geometry")] + sc,
            **meta(
                "beginner",
                ["transform", "move"],
                "Local Move uses translation state (or a Vector). The final Move-to-player is a separate translation.",
            ),
        )
    )

    box = n(
        "box",
        "geometry.primitives.box_from_corner_size",
        1,
        2,
        {"cornerX": 1.0, "cornerY": 0.0, "cornerZ": -1.0, "sizeX": 3.0, "sizeY": 2.0, "sizeZ": 2.0},
    )
    axis = vector_n("axis", 0.0, 1.0, 0.0, 1, 3)
    angle = float_n("angle", 45.0, 1, 4)
    rotate = n("rotate", "transform.basic_transforms.rotate_geometry_axis", 2, 2.5, {"defaultAngle": 45.0})
    sinks, sc = player_chain("rotate", "minecraft:cyan_concrete", sink_col=4)
    presets.append(
        preset(
            "operations.transform.rotate_axis",
            "Rotate Around Axis",
            "Box → Rotate Geometry Around Axis (Y, 45°) → Move to Player → voxel preview.",
            [box, axis, angle, rotate] + sinks,
            [
                c("box", "output_geometry", "rotate", "input_geometry"),
                c("axis", "output_vector", "rotate", "input_axis"),
                c("angle", "output_value", "rotate", "input_angle"),
            ]
            + sc,
            **meta(
                "beginner",
                ["transform", "rotate"],
                "Center defaults to origin when unconnected — keep geometry local before the player Move.",
            ),
        )
    )

    box = n(
        "box",
        "geometry.primitives.box_from_corner_size",
        1,
        2,
        {"cornerX": 0.0, "cornerY": 0.0, "cornerZ": 0.0, "sizeX": 2.0, "sizeY": 2.0, "sizeZ": 2.0},
    )
    scale_factor = float_n("scale", 2.0, 1, 3)
    scale = n("scale_node", "transform.basic_transforms.scale_geometry_point", 2, 2, {"defaultScale": 2.0})
    sinks, sc = player_chain("scale_node", "minecraft:lime_concrete", sink_col=4)
    presets.append(
        preset(
            "operations.transform.scale_point",
            "Scale Around Point",
            "Box → Scale Geometry Around Point (×2 at origin) → Move to Player → voxel preview.",
            [box, scale_factor, scale] + sinks,
            [
                c("box", "output_geometry", "scale_node", "input_geometry"),
                c("scale", "output_value", "scale_node", "input_scale"),
            ]
            + sc,
            **meta(
                "beginner",
                ["transform", "scale"],
                "Scale center defaults to the origin. Uniform scale via the Scale input (or defaultScale state).",
            ),
        )
    )

    box = n(
        "box",
        "geometry.primitives.box_from_corner_size",
        1,
        2,
        {"cornerX": 1.0, "cornerY": 0.0, "cornerZ": 0.0, "sizeX": 3.0, "sizeY": 2.0, "sizeZ": 2.0},
    )
    plane = n("mirror_plane", "reference.planes.world_plane", 1, 3, {"planePreset": "YZ", "originX": 0.0, "originY": 0.0, "originZ": 0.0})
    mirror = n("mirror", "transform.basic_transforms.mirror_geometry_plane", 2, 2.5)
    sinks, sc = player_chain("mirror", "minecraft:magenta_concrete", sink_col=4)
    presets.append(
        preset(
            "operations.transform.mirror_plane",
            "Mirror Across Plane",
            "Offset Box → Mirror Geometry Across YZ Plane → Move to Player → voxel preview.",
            [box, plane, mirror] + sinks,
            [
                c("box", "output_geometry", "mirror", "input_geometry"),
                c("mirror_plane", "output_plane", "mirror", "input_plane"),
            ]
            + sc,
            **meta(
                "beginner",
                ["transform", "mirror", "plane"],
                "World Plane presets: XY=+Z, YZ=+X, XZ=+Y. Mirror needs a connected Plane.",
            ),
        )
    )

    # --- Arrays ---
    box = n(
        "box",
        "geometry.primitives.box_from_corner_size",
        1,
        2,
        {"cornerX": 0.0, "cornerY": 0.0, "cornerZ": 0.0, "sizeX": 1.5, "sizeY": 1.5, "sizeZ": 1.5},
    )
    direction = vector_n("direction", 1.0, 0.0, 0.0, 1, 3)
    array = n("array", "pattern.linear.linear_array", 2, 2, {"distance": 3.0, "count": 5})
    sinks, sc = player_chain("array", "minecraft:iron_block", sink_col=4)
    presets.append(
        preset(
            "operations.array.linear",
            "Linear Array",
            "Box → Linear Array (5 copies, spacing 3 along +X) → Move to Player → voxel preview.",
            [box, direction, array] + sinks,
            [
                c("box", "output_geometry", "array", "input_geometry"),
                c("direction", "output_vector", "array", "input_direction"),
            ]
            + sc,
            **meta(
                "beginner",
                ["array", "linear", "pattern"],
                "Linear Array copies geometry along a direction. Count includes the original instance.",
            ),
        )
    )

    box = n(
        "box",
        "geometry.primitives.box_from_corner_size",
        1,
        2,
        {"cornerX": 4.0, "cornerY": 0.0, "cornerZ": -0.75, "sizeX": 1.5, "sizeY": 1.5, "sizeZ": 1.5},
    )
    axis = vector_n("axis", 0.0, 1.0, 0.0, 1, 3)
    array = n("array", "pattern.radial.polar_array", 2, 2, {"count": 6, "totalAngle": 360.0, "includeEnd": False})
    sinks, sc = player_chain("array", "minecraft:lapis_block", sink_col=4)
    presets.append(
        preset(
            "operations.array.polar",
            "Polar Array",
            "Offset Box → Polar Array (6 around Y, 360°) → Move to Player → voxel preview.",
            [box, axis, array] + sinks,
            [
                c("box", "output_geometry", "array", "input_geometry"),
                c("axis", "output_vector", "array", "input_axis"),
            ]
            + sc,
            **meta(
                "beginner",
                ["array", "polar", "pattern"],
                "Place the seed away from the center so copies form a ring. Center defaults to origin.",
            ),
        )
    )

    box = n(
        "box",
        "geometry.primitives.box_from_corner_size",
        1,
        2,
        {"cornerX": 0.0, "cornerY": 0.0, "cornerZ": 0.0, "sizeX": 1.2, "sizeY": 1.2, "sizeZ": 1.2},
    )
    array = n(
        "array",
        "pattern.grid.grid_array",
        2,
        2,
        {
            "xDistance": 3.0,
            "xCount": 3,
            "yDistance": 3.0,
            "yCount": 2,
            "zDistance": 3.0,
            "zCount": 3,
        },
    )
    sinks, sc = player_chain("array", "minecraft:emerald_block", sink_col=4)
    presets.append(
        preset(
            "operations.array.grid",
            "Grid Array",
            "Box → Grid Array (3×2×3) → Move to Player → voxel preview.",
            [box, array] + sinks,
            [c("box", "output_geometry", "array", "input_geometry")] + sc,
            **meta(
                "beginner",
                ["array", "grid", "pattern"],
                "Grid Array uses per-axis count/distance. Directions default to world X/Y/Z when unconnected.",
            ),
        )
    )

    # --- Booleans ---
    base = n(
        "base_box",
        "geometry.primitives.box_from_corner_size",
        1,
        2,
        {"cornerX": -3.0, "cornerY": 0.0, "cornerZ": -3.0, "sizeX": 6.0, "sizeY": 4.0, "sizeZ": 6.0},
    )
    cutter = n(
        "cutter_sphere",
        "geometry.primitives.sphere",
        1,
        3,
        {"centerX": 0.0, "centerY": 2.0, "centerZ": 0.0, "radius": 2.5},
    )
    diff = n("difference", "geometry.boolean.difference", 2, 2.5)
    sinks, sc = player_chain("difference", "minecraft:cobblestone", sink_col=4)
    presets.append(
        preset(
            "operations.boolean.difference",
            "Boolean Difference",
            "Box − Sphere → Difference → Move to Player → voxel preview.",
            [base, cutter, diff] + sinks,
            [
                c("base_box", "output_geometry", "difference", "input_base"),
                c("cutter_sphere", "output_geometry", "difference", "input_cutter"),
            ]
            + sc,
            **meta(
                "beginner",
                ["boolean", "difference"],
                "Difference subtracts cutter from base. Evaluate after local construction, then Move once.",
            ),
        )
    )

    left = n(
        "box",
        "geometry.primitives.box_from_corner_size",
        1,
        2,
        {"cornerX": -3.0, "cornerY": 0.0, "cornerZ": -3.0, "sizeX": 6.0, "sizeY": 4.0, "sizeZ": 6.0},
    )
    right = n(
        "sphere",
        "geometry.primitives.sphere",
        1,
        3,
        {"centerX": 0.0, "centerY": 2.0, "centerZ": 0.0, "radius": 3.5},
    )
    inter = n("intersection", "geometry.boolean.intersection", 2, 2.5)
    sinks, sc = player_chain("intersection", "minecraft:prismarine", sink_col=4)
    presets.append(
        preset(
            "operations.boolean.intersection",
            "Boolean Intersection",
            "Box ∩ Sphere → Intersection → Move to Player → voxel preview.",
            [left, right, inter] + sinks,
            [
                c("box", "output_geometry", "intersection", "input_left"),
                c("sphere", "output_geometry", "intersection", "input_right"),
            ]
            + sc,
            **meta(
                "beginner",
                ["boolean", "intersection"],
                "Intersection keeps only the overlapping volume of two geometries.",
            ),
        )
    )

    # --- Combine / copy instances ---
    b0 = n(
        "box_a",
        "geometry.primitives.box_from_corner_size",
        1,
        2,
        {"cornerX": 0.0, "cornerY": 0.0, "cornerZ": 0.0, "sizeX": 2.0, "sizeY": 2.0, "sizeZ": 2.0},
    )
    b1 = n(
        "box_b",
        "geometry.primitives.box_from_corner_size",
        1,
        3,
        {"cornerX": 3.0, "cornerY": 1.0, "cornerZ": 1.0, "sizeX": 2.0, "sizeY": 3.0, "sizeZ": 2.0},
    )
    b2 = n(
        "box_c",
        "geometry.primitives.box_from_corner_size",
        1,
        4,
        {"cornerX": 6.0, "cornerY": 0.0, "cornerZ": -1.0, "sizeX": 2.0, "sizeY": 2.5, "sizeZ": 3.0},
    )
    combine = n("combine", "geometry.combine.geometry", 2, 3, {"inputCount": 3})
    sinks, sc = player_chain("combine", "minecraft:quartz_block", sink_col=4)
    presets.append(
        preset(
            "operations.combine.three_boxes",
            "Combine Three Boxes",
            "Three offset boxes → Combine Geometry → Move to Player → voxel preview.",
            [b0, b1, b2, combine] + sinks,
            [
                c("box_a", "output_geometry", "combine", "input_geometry_0"),
                c("box_b", "output_geometry", "combine", "input_geometry_1"),
                c("box_c", "output_geometry", "combine", "input_geometry_2"),
            ]
            + sc,
            **meta(
                "beginner",
                ["combine", "copy", "instances"],
                "Combine is the non-destructive way to group separately authored solids into one preview chain.",
            ),
        )
    )

    # Advanced: tower = cylinder shell via difference + polar battlements-ish
    outer = n(
        "outer",
        "geometry.primitives.cylinder",
        1,
        2,
        {
            "startX": 0.0,
            "startY": 0.0,
            "startZ": 0.0,
            "endX": 0.0,
            "endY": 10.0,
            "endZ": 0.0,
            "radius": 4.0,
        },
    )
    inner = n(
        "inner",
        "geometry.primitives.cylinder",
        1,
        3,
        {
            "startX": 0.0,
            "startY": 0.0,
            "startZ": 0.0,
            "endX": 0.0,
            "endY": 10.0,
            "endZ": 0.0,
            "radius": 3.0,
        },
    )
    shell = n("shell", "geometry.boolean.difference", 2, 2.5)
    merlon = n(
        "merlon",
        "geometry.primitives.box_from_corner_size",
        1,
        4,
        {"cornerX": 3.2, "cornerY": 9.5, "cornerZ": -0.6, "sizeX": 1.2, "sizeY": 1.5, "sizeZ": 1.2},
    )
    merlon_array = n(
        "merlon_array",
        "pattern.radial.polar_array",
        2,
        4,
        {"count": 8, "totalAngle": 360.0, "includeEnd": False},
    )
    tower = n("tower", "geometry.combine.geometry", 3, 3, {"inputCount": 2})
    sinks, sc = player_chain("tower", "minecraft:stone_bricks", sink_col=5)
    presets.append(
        preset(
            "operations.advanced.hollow_tower",
            "Hollow Tower + Merlons",
            "Cylinder shell (Difference) + Polar Array merlons → Combine → Move → voxel preview.",
            [outer, inner, shell, merlon, merlon_array, tower] + sinks,
            [
                c("outer", "output_geometry", "shell", "input_base"),
                c("inner", "output_geometry", "shell", "input_cutter"),
                c("merlon", "output_geometry", "merlon_array", "input_geometry"),
                c("shell", "output_geometry", "tower", "input_geometry_0"),
                c("merlon_array", "output_geometry", "tower", "input_geometry_1"),
            ]
            + sc,
            **meta(
                "advanced",
                ["advanced", "boolean", "polar", "combine", "tower"],
                "Composes Difference + Polar Array + Combine — the same pattern used by showcase buildings.",
                ["Keep the hollow inner radius strictly less than outer radius."],
            ),
        )
    )

    return presets


def build_sphere_from_diameter() -> dict:
    a_nodes, a_conns, a_ref = construct_point("diameter_a", -4.0, 3.0, 0.0, 0, 2)
    b_nodes, b_conns, b_ref = construct_point("diameter_b", 4.0, 3.0, 0.0, 0, 5)
    sphere = n("sphere", "geometry.primitives.sphere_from_diameter", 2, 4)
    sinks, sc = player_chain("sphere", "minecraft:stone", sink_col=4)
    return preset(
        "quickstart.sphere_from_diameter",
        "Sphere From Diameter",
        "Two Construct Points define a diameter → Sphere From Diameter → Move to Player → voxel preview.",
        a_nodes + b_nodes + [sphere] + sinks,
        a_conns
        + b_conns
        + [
            c(a_ref, "output_point", "sphere", "input_start"),
            c(b_ref, "output_point", "sphere", "input_end"),
        ]
        + sc,
        **meta(
            "beginner",
            ["quickstart", "sphere", "construct_point"],
            "Sphere From Diameter needs two Points. Radius is half the distance between them.",
        ),
    )


def fix_modern_window(categories: list) -> None:
    for cat in categories:
        for p in cat.get("presets") or []:
            if p.get("id") != "building_elements.windows.modern_window":
                continue
            for node in p.get("nodes") or []:
                if node.get("ref") == "volume" and isinstance(node.get("state"), dict):
                    node["state"]["sizeY"] = 3.2
                if node.get("ref") == "opening_margin" and isinstance(node.get("state"), dict):
                    node["state"]["value"] = 0.3
            print("fixed modern_window volume.sizeY=3.2 opening_margin=0.3")
            return
    raise SystemExit("modern_window not found")


def upsert_preset(category: dict, preset_def: dict) -> None:
    presets = category.setdefault("presets", [])
    for i, existing in enumerate(presets):
        if existing and existing.get("id") == preset_def["id"]:
            presets[i] = preset_def
            return
    presets.append(preset_def)


def ensure_operations_category(categories: list) -> dict:
    for cat in categories:
        if cat.get("id") == "operations":
            return cat
    cat = {"id": "operations", "displayName": "Operations", "presets": []}
    # Insert after quickstart if present, else append
    for i, existing in enumerate(categories):
        if existing.get("id") == "quickstart":
            categories.insert(i + 1, cat)
            return cat
    categories.append(cat)
    return cat


def validate_layout(preset_def: dict) -> list[str]:
    errors = []
    nodes = [n for n in preset_def.get("nodes") or [] if n and n.get("ref")]
    for i, a in enumerate(nodes):
        for b in nodes[i + 1 :]:
            if abs(a["x"] - b["x"]) < 40.0 and abs(a["y"] - b["y"]) < 40.0:
                errors.append(
                    f"{preset_def['id']}: overlap {a['ref']}@({a['x']},{a['y']}) vs {b['ref']}@({b['x']},{b['y']})"
                )
    return errors


def main() -> None:
    data = json.loads(GRAPH.read_text(encoding="utf-8-sig"))
    categories = data["categories"]
    fix_modern_window(categories)

    quickstart = next(c for c in categories if c["id"] == "quickstart")
    upsert_preset(quickstart, build_sphere_from_diameter())

    ops = ensure_operations_category(categories)
    # Replace operations presets wholesale for idempotent regen
    built = build_operations()
    ops["presets"] = built

    layout_errors: list[str] = []
    for p in built + [build_sphere_from_diameter()]:
        layout_errors.extend(validate_layout(p))
    if layout_errors:
        print("LAYOUT ERRORS:")
        for e in layout_errors:
            print(" ", e)
        raise SystemExit(1)

    GRAPH.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"wrote {GRAPH}")
    print(f"operations presets: {len(built)}")
    print("quickstart.sphere_from_diameter upserted")


if __name__ == "__main__":
    main()
