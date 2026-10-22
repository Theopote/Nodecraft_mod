package com.nodecraft.gui.editor.impl;

import com.nodecraft.gui.editor.session.NodeDisplayMode;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeVisualLayoutTest {

    private static final float LINE_H = 16f;
    private static final float SPACING_Y = 4f;

    /** Approx. ImGui-scale measurer so long labels exceed TEXT_ONLY min width. */
    private static final NodeVisualLayout.TextWidthMeasurer CHAR_WIDTH = text ->
            text == null ? 0f : text.length() * 8f;

    @Test
    void textOnlyUsesFullPortLabelsAndWidensForLongNames() {
        StubNode node = StubNode.named("Wall With Openings")
                .inputs("Wall Geometry", "Opening Profiles")
                .outputs("Geometry", "Top Edges");

        NodeVisualLayout.Result layout = NodeVisualLayout.compute(
                node, NodeDisplayMode.TEXT_ONLY, node.inputs, node.outputs,
                CHAR_WIDTH, LINE_H, SPACING_Y);

        assertEquals(List.of("Wall Geometry", "Opening Profiles"), layout.inputLabels());
        assertEquals(List.of("Geometry", "Top Edges"), layout.outputLabels());
        assertTrue(layout.showTitle());
        assertFalse(layout.showIcon());
        assertTrue(layout.width() > NodeRenderConstants.TEXT_ONLY_MIN_NODE_WIDTH_UNSCALED);

        StubNode shortNode = StubNode.named("A")
                .inputs("W")
                .outputs("G");
        NodeVisualLayout.Result shortLayout = NodeVisualLayout.compute(
                shortNode, NodeDisplayMode.TEXT_ONLY, shortNode.inputs, shortNode.outputs,
                CHAR_WIDTH, LINE_H, SPACING_Y);
        assertTrue(layout.width() > shortLayout.width());
    }

    @Test
    void iconOnlyUsesFirstLettersAndIgnoresLongNameWidth() {
        StubNode longPorts = StubNode.named("Wall")
                .inputs("Wall Geometry With Extremely Long Name")
                .outputs("Geometry Output That Is Also Very Long");
        StubNode shortPorts = StubNode.named("Wall")
                .inputs("W")
                .outputs("G");

        NodeVisualLayout.Result longLayout = NodeVisualLayout.compute(
                longPorts, NodeDisplayMode.ICON_ONLY, longPorts.inputs, longPorts.outputs,
                CHAR_WIDTH, LINE_H, SPACING_Y);
        NodeVisualLayout.Result shortLayout = NodeVisualLayout.compute(
                shortPorts, NodeDisplayMode.ICON_ONLY, shortPorts.inputs, shortPorts.outputs,
                CHAR_WIDTH, LINE_H, SPACING_Y);

        assertEquals(List.of("W"), longLayout.inputLabels());
        assertEquals(List.of("G"), longLayout.outputLabels());
        assertTrue(longLayout.showIcon());
        assertFalse(longLayout.showTitle());
        assertEquals(longLayout.width(), shortLayout.width(), 0.01f);
        assertTrue(longLayout.width() <= NodeRenderConstants.ICON_ONLY_MAX_NODE_WIDTH_UNSCALED);
        assertTrue(longLayout.width() >= NodeRenderConstants.ICON_ONLY_MIN_NODE_WIDTH_UNSCALED);
    }

    @Test
    void iconOnlyCaptionStaysBetweenPortLetterColumns() {
        StubNode node = StubNode.named("Very Long Node Display Name That Would Overlap")
                .inputs("Wall Geometry", "Opening Profiles")
                .outputs("Geometry", "Top Edges", "Center Lines");

        NodeVisualLayout.Result layout = NodeVisualLayout.compute(
                node, NodeDisplayMode.ICON_ONLY, node.inputs, node.outputs,
                CHAR_WIDTH, LINE_H, SPACING_Y);

        float letterW = CHAR_WIDTH.measure("W");
        float inputCol = NodeRenderConstants.PORT_RADIUS_UNSCALED * 2f
                + NodeRenderConstants.PORT_CIRCLE_TO_TEXT_PADDING
                + letterW;
        float outputCol = letterW
                + NodeRenderConstants.PORT_CIRCLE_TO_TEXT_PADDING
                + NodeRenderConstants.PORT_RADIUS_UNSCALED * 2f;

        assertTrue(layout.captionMaxWidth() > 0f);
        assertTrue(layout.captionMaxWidth() <= layout.width() - inputCol - outputCol + 0.01f);
        assertTrue(CHAR_WIDTH.measure(layout.caption()) > layout.captionMaxWidth());
    }

    @Test
    void portCountsAndOrderStableAcrossModes() {
        StubNode node = StubNode.named("Node")
                .inputs("Alpha", "Beta", "Gamma")
                .outputs("OutA", "OutB");

        NodeVisualLayout.Result text = NodeVisualLayout.compute(
                node, NodeDisplayMode.TEXT_ONLY, node.inputs, node.outputs, CHAR_WIDTH, LINE_H, SPACING_Y);
        NodeVisualLayout.Result icon = NodeVisualLayout.compute(
                node, NodeDisplayMode.ICON_ONLY, node.inputs, node.outputs, CHAR_WIDTH, LINE_H, SPACING_Y);

        assertEquals(3, text.inputLabels().size());
        assertEquals(3, icon.inputLabels().size());
        assertEquals(2, text.outputLabels().size());
        assertEquals(2, icon.outputLabels().size());
        assertEquals("A", icon.inputLabels().get(0));
        assertEquals("B", icon.inputLabels().get(1));
        assertEquals("G", icon.inputLabels().get(2));
        assertTrue(text.portRegionTop() > 0f);
        assertTrue(icon.portRegionTop() > 0f);
        assertTrue(text.portsRegionHeight() > 0f);
        assertEquals(text.portsRegionHeight(), icon.portsRegionHeight(), 0.01f);
    }

    @Test
    void duplicateInitialsDoNotChangePortIdentity() {
        StubPort geometry = new StubPort("port_geometry", "Geometry", true);
        StubPort grid = new StubPort("port_grid", "Grid", true);
        StubNode node = StubNode.named("Dup").withInputs(List.of(geometry, grid)).withOutputs(List.of());

        NodeVisualLayout.Result layout = NodeVisualLayout.compute(
                node, NodeDisplayMode.ICON_ONLY, node.inputs, node.outputs, CHAR_WIDTH, LINE_H, SPACING_Y);

        assertEquals("G", layout.inputLabels().get(0));
        assertEquals("G", layout.inputLabels().get(1));
        assertEquals("port_geometry", node.inputs.get(0).getId());
        assertEquals("port_grid", node.inputs.get(1).getId());
    }

    @Test
    void compactRerouteSizeIgnoresDisplayMode() {
        StubNode reroute = StubNode.reroute();
        NodeVisualLayout.Result text = NodeVisualLayout.compute(
                reroute, NodeDisplayMode.TEXT_ONLY, List.of(), List.of(), CHAR_WIDTH, LINE_H, SPACING_Y);
        NodeVisualLayout.Result icon = NodeVisualLayout.compute(
                reroute, NodeDisplayMode.ICON_ONLY, List.of(), List.of(), CHAR_WIDTH, LINE_H, SPACING_Y);

        assertTrue(text.compactReroute());
        assertTrue(icon.compactReroute());
        assertEquals(NodeRenderConstants.REROUTE_NODE_WIDTH_UNSCALED, text.width(), 0.01f);
        assertEquals(NodeRenderConstants.REROUTE_NODE_HEIGHT_UNSCALED, text.height(), 0.01f);
        assertEquals(text.width(), icon.width(), 0.01f);
        assertEquals(text.height(), icon.height(), 0.01f);
    }

    @Test
    void customUiExpandsLayoutInBothDisplayModes() {
        StubNode node = StubNode.named("Slider").inputs("Value").outputs("Out");
        NodeVisualLayout.CustomUIMetricsProvider provider = ignored ->
                new NodeVisualLayout.CustomUIMetrics(true, 48f, 200f);

        NodeVisualLayout.Result textBase = NodeVisualLayout.compute(
                node, NodeDisplayMode.TEXT_ONLY, node.inputs, node.outputs,
                CHAR_WIDTH, LINE_H, SPACING_Y, ignored -> NodeVisualLayout.CustomUIMetrics.NONE);
        NodeVisualLayout.Result text = NodeVisualLayout.compute(
                node, NodeDisplayMode.TEXT_ONLY, node.inputs, node.outputs,
                CHAR_WIDTH, LINE_H, SPACING_Y, provider);
        NodeVisualLayout.Result icon = NodeVisualLayout.compute(
                node, NodeDisplayMode.ICON_ONLY, node.inputs, node.outputs,
                CHAR_WIDTH, LINE_H, SPACING_Y, provider);

        assertFalse(textBase.hasCustomUI());
        assertTrue(text.hasCustomUI());
        assertTrue(icon.hasCustomUI());
        assertEquals(48f, text.customUIHeight(), 0.01f);
        assertEquals(textBase.height(), text.customUIRegionTop(), 0.01f);
        assertEquals(textBase.height() + 48f + NodeRenderConstants.NODE_HORIZONTAL_PADDING,
                text.height(), 0.01f);
        assertTrue(text.width() >= 200f + 2f * NodeRenderConstants.NODE_HORIZONTAL_PADDING);
        assertTrue(icon.height() > icon.headerHeight());
        assertFalse(text.showIcon());
        assertTrue(icon.showIcon());
    }

    private static final class StubPort implements IPort {
        private final String id;
        private final String displayName;
        private final boolean input;

        StubPort(String id, String displayName, boolean input) {
            this.id = id;
            this.displayName = displayName;
            this.input = input;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public NodeDataType getDataType() {
            return NodeDataType.ANY;
        }

        @Override
        public boolean isInput() {
            return input;
        }

        @Override
        public boolean isConnected() {
            return false;
        }

        @Override
        public INode getNode() {
            return null;
        }

        @Override
        public void setValue(Object value) {
        }

        @Override
        public Object getValue() {
            return null;
        }

        @Override
        public boolean connectTo(IPort port) {
            return false;
        }

        @Override
        public void disconnect() {
        }
    }

    private static final class StubNode implements INode {
        private final UUID id = UUID.randomUUID();
        private final String typeId;
        private final String displayName;
        private List<IPort> inputs = List.of();
        private List<IPort> outputs = List.of();

        private StubNode(String typeId, String displayName) {
            this.typeId = typeId;
            this.displayName = displayName;
        }

        static StubNode named(String displayName) {
            return new StubNode("test.node", displayName);
        }

        static StubNode reroute() {
            return new StubNode(NodeRenderConstants.REROUTE_NODE_TYPE_ID, "Reroute");
        }

        StubNode inputs(String... names) {
            StubPort[] ports = new StubPort[names.length];
            for (int i = 0; i < names.length; i++) {
                ports[i] = new StubPort("in_" + i, names[i], true);
            }
            this.inputs = List.of(ports);
            return this;
        }

        StubNode outputs(String... names) {
            StubPort[] ports = new StubPort[names.length];
            for (int i = 0; i < names.length; i++) {
                ports[i] = new StubPort("out_" + i, names[i], false);
            }
            this.outputs = List.of(ports);
            return this;
        }

        StubNode withInputs(List<IPort> ports) {
            this.inputs = ports;
            return this;
        }

        StubNode withOutputs(List<IPort> ports) {
            this.outputs = ports;
            return this;
        }

        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public String getTypeId() {
            return typeId;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public List<IPort> getInputPorts() {
            return inputs;
        }

        @Override
        public List<IPort> getOutputPorts() {
            return outputs;
        }

        @Override
        public double getPositionX() {
            return 0;
        }

        @Override
        public double getPositionY() {
            return 0;
        }

        @Override
        public void setPosition(double x, double y) {
        }

        @Override
        public Map<String, Object> compute(Map<String, Object> inputs) {
            return Map.of();
        }

        @Override
        public Object getOutput(String portId) {
            return null;
        }

        @Override
        public Object getInput(String portId) {
            return null;
        }

        @Override
        public void setInput(String portId, Object value) {
        }

        @Override
        public Object getNodeState() {
            return null;
        }

        @Override
        public void setNodeState(Object state) {
        }
    }
}
