package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.DifferenceGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.SdfGeometryData;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.nodes.geometry.voxel.VoxelizeGeometryNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.GeometryVoxelizationResult;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import com.nodecraft.nodesystem.util.VoxelInputUtils;
import com.nodecraft.nodesystem.util.VoxelizationStatus;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Language fence for Geometry Voxel Language v1 (Graph V95).
 */
class GeometryVoxelLanguageContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsAtLeastV95() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }


    @Test
    void voxelCategoryHasSingleNode() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.startsWith("geometry.voxel."))
            .sorted()
            .toList();
        assertEquals(1, ids.size());
        assertEquals("geometry.voxel.voxelize_geometry", ids.getFirst());
        NodeInfo info = registry.getNodeInfo(ids.getFirst());
        assertNotNull(info);
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(info.getNodeClass(), ids.getFirst()));
    }

    @Test
    void voxelizeGeometryExposesStrictOutputs() {
        assertPortType("geometry.voxel.voxelize_geometry", "output_valid", false, NodeDataType.BOOLEAN);
        assertPortType("geometry.voxel.voxelize_geometry", "output_error", false, NodeDataType.STRING);
        assertPortType("geometry.voxel.voxelize_geometry", "output_status", false, NodeDataType.STRING);
        assertPortType("geometry.voxel.voxelize_geometry", "output_blocks", false, NodeDataType.BLOCK_LIST);
        assertPortType("geometry.voxel.voxelize_geometry", "output_blocks_tree", false, NodeDataType.DATA_TREE);
        assertPortType("geometry.voxel.voxelize_geometry", "output_region", false, NodeDataType.REGION);
        assertPortType("geometry.voxel.voxelize_geometry", "output_count", false, NodeDataType.INTEGER);
    }

    @Test
    void oversizedGeometry_reportsOverBudgetNotFakeEmptySuccess() {
        VoxelizeGeometryNode node = new VoxelizeGeometryNode();
        node.setInput("input_geometry", oversizedBox());
        node.processNode(null);

        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertEquals("OVER_BUDGET", node.getOutput("output_status"));
        assertEquals(0, node.getOutput("output_count"));
        assertNull(node.getOutput("output_region"));
        assertTrue(String.valueOf(node.getOutput("output_error")).toLowerCase().contains("budget")
            || String.valueOf(node.getOutput("output_error")).contains("262144"));
    }

    @Test
    void unsupportedGeometry_failsClosed() {
        VoxelizeGeometryNode node = new VoxelizeGeometryNode();
        node.setInput("input_geometry", new GeometryData() {
        });
        node.processNode(null);

        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertEquals("UNSUPPORTED", node.getOutput("output_status"));
        assertNull(node.getOutput("output_region"));
    }

    @Test
    void legalEmptyDifference_isSuccessEmpty() {
        BoxGeometryData box = unitBox(0, 0, 0);
        VoxelizeGeometryNode node = new VoxelizeGeometryNode();
        node.setInput("input_geometry", new DifferenceGeometryData(box, box));
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(0, node.getOutput("output_count"));
        assertEquals("SUCCESS", node.getOutput("output_status"));
    }

    @Test
    void geometryTreeIllegalMember_failsClosed() {
        VoxelizeGeometryNode node = new VoxelizeGeometryNode();
        connectInput(node, "input_geometry_tree", NodeDataType.DATA_TREE);
        node.setInput("input_geometry_tree", new DataTreeData(List.of(
            new DataTreeData.Branch(List.of(0), List.of(unitBox(0, 0, 0), "garbage"))
        )));
        node.setInput("input_geometry", unitBox(5, 0, 0));
        node.processNode(null);

        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertEquals(0, node.getOutput("output_count"));
        assertTrue(String.valueOf(node.getOutput("output_error")).contains("GEOMETRY"));
    }

    @Test
    void geometryTreeOneOverBudgetChild_failsWholeNode() {
        VoxelizeGeometryNode node = new VoxelizeGeometryNode();
        connectInput(node, "input_geometry_tree", NodeDataType.DATA_TREE);
        node.setInput("input_geometry_tree", new DataTreeData(List.of(
            new DataTreeData.Branch(List.of(0), List.of(unitBox(0, 0, 0))),
            new DataTreeData.Branch(List.of(1), List.of(oversizedBox()))
        )));
        node.processNode(null);

        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertEquals(0, node.getOutput("output_count"));
    }

    @Test
    void connectedEmptyTree_doesNotFallbackToGeometry() {
        VoxelizeGeometryNode node = new VoxelizeGeometryNode();
        connectInput(node, "input_geometry_tree", NodeDataType.DATA_TREE);
        node.setInput("input_geometry_tree", DataTreeData.empty());
        node.setInput("input_geometry", unitBox(0, 0, 0));
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(0, node.getOutput("output_count"));
        assertEquals("SUCCESS", node.getOutput("output_status"));
    }

    @Test
    void compositeAggregateOverflow_reportsOverBudget() {
        BoxGeometryData sample = new BoxGeometryData(
            new Vector3d(0.5d, 0.5d, 0.5d),
            new Vector3d(3.5d, 3.5d, 3.5d)
        );
        int blocksPerChild = GeometryVoxelizer.voxelizeStrict(sample, true).blocks().size();
        assertTrue(blocksPerChild > 0);

        List<GeometryData> children = new ArrayList<>();
        int childCount = (int) (GenerationLimits.MAX_GEOMETRY_VOXELS / blocksPerChild) + 2;
        for (int i = 0; i < childCount; i++) {
            children.add(new BoxGeometryData(
                new Vector3d(i * 20 + 0.5d, 0.5d, 0.5d),
                new Vector3d(3.5d, 3.5d, 3.5d)
            ));
        }
        GeometryVoxelizationResult result = GeometryVoxelizer.voxelizeStrict(
            new CompositeGeometryData(children),
            true
        );
        assertFalse(result.success(), "status=" + result.status() + " error=" + result.error());
        assertEquals(VoxelizationStatus.OVER_BUDGET, result.status());
    }

    @Test
    void geometryTreeDuplicateChildren_doesNotDuplicateBranchBlocks() {
        BoxGeometryData box = new BoxGeometryData(
            new Vector3d(0.5d, 0.5d, 0.5d),
            new Vector3d(3.5d, 3.5d, 3.5d)
        );
        int uniqueCount = GeometryVoxelizer.voxelizeStrict(box, true).blocks().size();
        assertTrue(uniqueCount > 1);

        VoxelizeGeometryNode node = new VoxelizeGeometryNode();
        connectInput(node, "input_geometry_tree", NodeDataType.DATA_TREE);
        node.setInput("input_geometry_tree", new DataTreeData(List.of(
            new DataTreeData.Branch(List.of(0), List.of(box, box, box))
        )));
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(uniqueCount, node.getOutput("output_count"));
        DataTreeData blocksTree = (DataTreeData) node.getOutput("output_blocks_tree");
        assertNotNull(blocksTree);
        assertEquals(1, blocksTree.getBranchCount());
        assertEquals(uniqueCount, blocksTree.getBranches().getFirst().items().size());
    }

    @Test
    void geometryTreeMaterializedOutputOverBudget_failsClosed() {
        BoxGeometryData box = new BoxGeometryData(
            new Vector3d(0.5d, 0.5d, 0.5d),
            new Vector3d(3.5d, 3.5d, 3.5d)
        );
        int blocksPerBranch = GeometryVoxelizer.voxelizeStrict(box, true).blocks().size();
        assertTrue(blocksPerBranch > 0);

        int branchCount = (int) (GenerationLimits.MAX_VOXEL_TREE_BLOCK_ITEMS / blocksPerBranch) + 1;
        List<DataTreeData.Branch> branches = new ArrayList<>(branchCount);
        for (int i = 0; i < branchCount; i++) {
            branches.add(new DataTreeData.Branch(List.of(i), List.of(box)));
        }

        VoxelInputUtils.TreeVoxelizationOutcome outcome =
            VoxelInputUtils.voxelizeGeometryTree(new DataTreeData(branches), true);
        assertFalse(outcome.success());
        assertEquals(VoxelizationStatus.OVER_BUDGET, outcome.status());
        assertTrue(outcome.blocks().isEmpty());
        assertTrue(outcome.error().contains("MAX_VOXEL_TREE_BLOCK_ITEMS")
            || outcome.error().toLowerCase().contains("materialized"));
    }

    @Test
    void sdfNonFiniteSample_reportsEvaluationFailure() {
        SdfGeometryData nanSdf = new SdfGeometryData(
            point -> Double.NaN,
            new Vector3d(0.0d, 0.0d, 0.0d),
            new Vector3d(2.0d, 2.0d, 2.0d),
            0.0d
        );
        GeometryVoxelizationResult result = GeometryVoxelizer.voxelizeStrict(nanSdf, true);
        assertFalse(result.success());
        assertEquals(VoxelizationStatus.EVALUATION_FAILURE, result.status());
    }

    private static BoxGeometryData unitBox(double cx, double cy, double cz) {
        return new BoxGeometryData(new Vector3d(cx + 0.5d, cy + 0.5d, cz + 0.5d), new Vector3d(0.5d, 0.5d, 0.5d));
    }

    private static BoxGeometryData oversizedBox() {
        return new BoxGeometryData(new Vector3d(0, 0, 0), new Vector3d(33, 33, 33));
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = registry.createNodeInstance(typeId);
        assertNotNull(node, typeId);
        List<? extends IPort> ports = input ? node.getInputPorts() : node.getOutputPorts();
        IPort port = ports.stream().filter(p -> portId.equals(p.getId())).findFirst().orElse(null);
        assertNotNull(port, typeId + " missing port " + portId);
        assertEquals(expected, port.getDataType(), typeId + "." + portId);
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
            .filter(port -> inputPortId.equals(port.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(output.connectTo(input));
    }

    private static final class PortStubNode extends BaseNode {
        PortStubNode(NodeDataType outputType) {
            super(UUID.randomUUID(), "test.port_stub");
            addOutputPort(new BasePort("output_stub", "Stub", "", outputType, this));
        }

        @Override
        public void processNode(com.nodecraft.nodesystem.execution.ExecutionContext context) {
        }
    }
}
