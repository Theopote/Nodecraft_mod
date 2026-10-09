package com.nodecraft.nodesystem.semantic;

import com.nodecraft.nodesystem.api.NodeEffect;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeSemanticDeriverHardenTest {

    @Test
    void windowFrameIsNotArrayCapability() {
        Set<NodeCapability> caps = NodeSemanticDeriver.deriveCapabilities(
                "geometry.architectural_primitives.window_frame",
                "geometry.architectural_primitives",
                NodeEffect.PURE);
        assertTrue(caps.contains(NodeCapability.WINDOW));
        assertFalse(caps.contains(NodeCapability.ARRAY));
    }

    @Test
    void windowArrayKeepsArrayAndOpening() {
        Set<NodeCapability> caps = NodeSemanticDeriver.deriveCapabilities(
                "geometry.architectural_primitives.window_array",
                "geometry.architectural_primitives",
                NodeEffect.PURE);
        assertTrue(caps.contains(NodeCapability.WINDOW));
        assertTrue(caps.contains(NodeCapability.OPENING));
        assertTrue(caps.contains(NodeCapability.ARRAY));
    }

    @Test
    void doorArrayKeepsArray_nonArrayDoorWouldNot() {
        Set<NodeCapability> arrayDoor = NodeSemanticDeriver.deriveCapabilities(
                "geometry.architectural_primitives.door_array",
                "geometry.architectural_primitives",
                NodeEffect.PURE);
        assertTrue(arrayDoor.contains(NodeCapability.OPENING));
        assertTrue(arrayDoor.contains(NodeCapability.ARRAY));

        Set<NodeCapability> simpleDoor = NodeSemanticDeriver.deriveCapabilities(
                "geometry.architectural_primitives.simple_door",
                "geometry.architectural_primitives",
                NodeEffect.PURE);
        assertTrue(simpleDoor.contains(NodeCapability.OPENING));
        assertFalse(simpleDoor.contains(NodeCapability.ARRAY));
    }

    @Test
    void booleanIntersectionIsNotBooleanCut() {
        Set<NodeCapability> caps = NodeSemanticDeriver.deriveCapabilities(
                "geometry.boolean.intersection",
                "geometry.boolean",
                NodeEffect.PURE);
        assertFalse(caps.contains(NodeCapability.BOOLEAN_CUT));
    }

    @Test
    void differenceIsBooleanCut() {
        Set<NodeCapability> caps = NodeSemanticDeriver.deriveCapabilities(
                "geometry.boolean.difference",
                "geometry.boolean",
                NodeEffect.PURE);
        assertTrue(caps.contains(NodeCapability.BOOLEAN_CUT));
    }

    @Test
    void outputExecuteStatusIsNotWorldApply() {
        Set<NodeCapability> caps = NodeSemanticDeriver.deriveCapabilities(
                "output.execute.bake_status",
                "output.execute",
                NodeEffect.CONTEXT_READ);
        assertFalse(caps.contains(NodeCapability.WORLD_APPLY));
        assertFalse(caps.contains(NodeCapability.APPLY));
    }

    @Test
    void surfaceStripToBlocksIsPureVoxelizeNotWorldApply() {
        Set<NodeCapability> caps = NodeSemanticDeriver.deriveCapabilities(
                "geometry.voxel.surface_strip_to_blocks",
                "geometry.voxel",
                NodeEffect.PURE);
        assertTrue(caps.contains(NodeCapability.VOXELIZE));
        assertFalse(caps.contains(NodeCapability.WORLD_APPLY));
        assertFalse(caps.contains(NodeCapability.APPLY));
    }

    @Test
    void worldWriteEffectStillTagsWorldApply() {
        Set<NodeCapability> caps = NodeSemanticDeriver.deriveCapabilities(
                "world.write.set_block",
                "world.write",
                NodeEffect.WORLD_WRITE);
        assertTrue(caps.contains(NodeCapability.WORLD_APPLY));
    }
}
