package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.datatypes.BlockInfoData;
import com.nodecraft.nodesystem.datatypes.EntityInfoData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.input.context.PlayerPositionNode;
import com.nodecraft.nodesystem.nodes.input.context.PlayerRaycastNode;
import com.nodecraft.nodesystem.util.BlockStateData;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Input Context Snapshot Data Contract v2 (Graph V112).
 */
class InputContextLanguageV2ContractTest {

    @Test
    void currentGraphFormatIsAtLeastV112() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void raycastPortsBindToSnapshotDataClasses() {
        PlayerRaycastNode node = new PlayerRaycastNode();
        assertEquals(NodeDataType.BLOCK_INFO, findPort(node.getOutputPorts(), "output_hit_block").getDataType());
        assertEquals(NodeDataType.ENTITY_INFO, findPort(node.getOutputPorts(), "output_hit_entity").getDataType());
        assertEquals(BlockInfoData.class, NodeDataType.BLOCK_INFO.getJavaClass());
        assertEquals(EntityInfoData.class, NodeDataType.ENTITY_INFO.getJavaClass());
    }

    @Test
    void blockInfoDataFactoryBuildsSnapshot() {
        BlockPos pos = new BlockPos(1, 2, 3);
        // fromBlockState with null state → null
        assertNull(BlockInfoData.fromBlockState(pos, null));

        BlockInfoData synthetic = new BlockInfoData(
            pos,
            "minecraft:stone",
            new BlockStateData(),
            false
        );
        assertEquals(pos, synthetic.position());
        assertEquals("minecraft:stone", synthetic.blockId());
        assertFalse(synthetic.isAir());
        assertNotNull(synthetic.stateProperties());
    }

    @Test
    void entityInfoDataRejectsNullEntity() {
        assertNull(EntityInfoData.fromEntity(null));
    }

    @Test
    void entityInfoDataConstructorSnapshotsFields() {
        UUID id = UUID.randomUUID();
        EntityInfoData info = new EntityInfoData(
            id,
            "minecraft:pig",
            new com.nodecraft.nodesystem.datatypes.PointData(1, 2, 3),
            new com.nodecraft.nodesystem.datatypes.PointData(0, 0, 0),
            new com.nodecraft.nodesystem.datatypes.PointData(1, 1, 1),
            "Pig"
        );
        assertEquals(id, info.uuid());
        assertEquals("minecraft:pig", info.entityTypeId());
        assertEquals(1.0d, info.position().getX(), 0.0d);
        assertEquals("Pig", info.displayName());
    }

    @Test
    void positionProcessNodeWithNullPlayerContextDoesNotClientFallback() {
        PlayerPositionNode node = new PlayerPositionNode();
        assertFalse(node.hasCachedPosition());

        // World null + player null: runtime path must not invent a client snapshot.
        ExecutionContext context = new ExecutionContext(null, null);
        node.processNode(context);

        assertFalse(node.hasCachedPosition());
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertNull(node.getOutput("output_position"));
    }

    @Test
    void blockInfoAcceptsLegacyBlockIdString() {
        assertTrue(NodeDataType.BLOCK_INFO.isCompatible("minecraft:dirt"));
        assertTrue(NodeDataType.BLOCK_INFO.isCompatible(
            new BlockInfoData(new BlockPos(0, 0, 0), "minecraft:dirt", new BlockStateData(), false)
        ));
        assertFalse(NodeDataType.ENTITY_INFO.isCompatible("not-an-entity"));
    }

    private static IPort findPort(Iterable<IPort> ports, String id) {
        for (IPort port : ports) {
            if (id.equals(port.getId())) {
                return port;
            }
        }
        throw new AssertionError("missing port " + id);
    }
}
