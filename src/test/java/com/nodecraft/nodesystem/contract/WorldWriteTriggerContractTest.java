package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.nodes.geometry.voxel.VoxelizeGeometryNode;
import com.nodecraft.nodesystem.nodes.output.execute.ApplyChangesNode;
import com.nodecraft.nodesystem.nodes.output.execute.RedoLastBakeNode;
import com.nodecraft.nodesystem.nodes.output.execute.UndoLastBakeNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freeze fence: world-write triggers are EXEC pulses (not ANY), and Voxelize stays PURE.
 */
class WorldWriteTriggerContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void applyUndoRedoTriggerPortsAreExec() {
        assertTriggerIsExec(new ApplyChangesNode());
        assertTriggerIsExec(new UndoLastBakeNode());
        assertTriggerIsExec(new RedoLastBakeNode());
    }

    @Test
    void catalogWorldWriteBakeTriggersAreExec() {
        assertCatalogTriggerIsExec("output.execute.apply_changes");
        assertCatalogTriggerIsExec("output.execute.undo_last_bake");
        assertCatalogTriggerIsExec("output.execute.redo_last_bake");
    }

    @Test
    void voxelizeGeometryIsPureNotWorldWrite() {
        VoxelizeGeometryNode node = new VoxelizeGeometryNode();
        assertEquals("geometry.voxel.voxelize_geometry", node.getTypeId());
        com.nodecraft.nodesystem.api.NodeInfo info =
                VoxelizeGeometryNode.class.getAnnotation(com.nodecraft.nodesystem.api.NodeInfo.class);
        assertNotNull(info);
        assertEquals(NodeEffect.PURE, info.effect());
        assertEquals(
                NodeEffect.PURE,
                NodeEffectResolver.resolve(VoxelizeGeometryNode.class, "geometry.voxel.voxelize_geometry")
        );
    }

    @Test
    void dataTypesCannotConnectIntoExecTrigger() {
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.EXEC, NodeDataType.EXEC));
        assertFalse(NodeDataType.isConnectableTo(NodeDataType.INTEGER, NodeDataType.EXEC));
        assertFalse(NodeDataType.isConnectableTo(NodeDataType.STRING, NodeDataType.EXEC));
        assertFalse(NodeDataType.isConnectableTo(NodeDataType.GEOMETRY, NodeDataType.EXEC));
        assertFalse(NodeDataType.isConnectableTo(NodeDataType.ANY, NodeDataType.EXEC));
    }

    private static void assertTriggerIsExec(INode node) {
        IPort trigger = findInput(node, "input_trigger");
        assertNotNull(trigger, node.getTypeId() + " missing input_trigger");
        assertEquals(NodeDataType.EXEC, trigger.getDataType(), node.getTypeId() + " Trigger must be EXEC");
        assertFalse(trigger.isRequired(), node.getTypeId() + " Trigger must be optional (manual UI path)");
    }

    private static void assertCatalogTriggerIsExec(String typeId) {
        var info = registry.getNodeInfo(typeId);
        assertNotNull(info, typeId);
        assertEquals(NodeEffect.WORLD_WRITE, NodeEffectResolver.resolve(info.getNodeClass(), typeId), typeId);
        INode instance = registry.createNodeInstance(typeId);
        assertTriggerIsExec(instance);
    }

    private static IPort findInput(INode node, String portId) {
        for (IPort port : node.getInputPorts()) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        return null;
    }
}
