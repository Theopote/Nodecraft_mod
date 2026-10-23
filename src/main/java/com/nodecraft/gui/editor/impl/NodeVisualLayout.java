package com.nodecraft.gui.editor.impl;

import com.nodecraft.gui.editor.session.NodeDisplayMode;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.nodes.utilities.assist.RelayNode;
import com.nodecraft.nodesystem.nodes.utilities.organization.SubgraphNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared unscaled node layout for TEXT_ONLY / ICON_ONLY modes.
 * Dimensions, port labels, and header/icon regions must stay consistent
 * between sizing, drawing, and port hit testing.
 */
public final class NodeVisualLayout {

    @FunctionalInterface
    public interface TextWidthMeasurer {
        float measure(String text);
    }

    /** Unscaled custom UI metrics for layout sizing. */
    public record CustomUIMetrics(boolean enabled, float height, float minWidth) {
        public static final CustomUIMetrics NONE = new CustomUIMetrics(false, 0f, 0f);
    }

    @FunctionalInterface
    public interface CustomUIMetricsProvider {
        CustomUIMetrics metricsFor(INode node);
    }

    /**
     * Layout result in unscaled (logical) coordinates relative to the node top-left.
     */
    public record Result(
            float width,
            float height,
            float headerHeight,
            boolean showTitle,
            boolean showIcon,
            float iconSize,
            float iconLeft,
            float iconTop,
            float captionTop,
            String caption,
            /**
             * Max unscaled width for the ICON_ONLY caption so it stays between
             * the left/right port-letter columns and does not overlap them.
             */
            float captionMaxWidth,
            float portsRegionHeight,
            /** Unscaled Y where the first port row starts (top of text line), relative to node top. */
            float portRegionTop,
            List<String> inputLabels,
            List<String> outputLabels,
            boolean hasCustomUI,
            float customUIHeight,
            /** Unscaled Y where the custom UI region begins (below title/ports). */
            float customUIRegionTop,
            boolean compactReroute
    ) {
    }

    /** Resolve custom UI metrics from {@link ICustomUINode} when present. */
    public static CustomUIMetrics metricsFromNode(INode node) {
        if (node instanceof ICustomUINode customUINode && customUINode.hasCustomUI()) {
            float height = customUINode.getCustomUIHeight();
            if (height > 0f) {
                return new CustomUIMetrics(true, height, customUINode.getMinRequiredUIWidth());
            }
        }
        return CustomUIMetrics.NONE;
    }

    private NodeVisualLayout() {
    }

    public static String nodeTitle(INode node) {
        if (node == null) {
            return "";
        }
        return node instanceof SubgraphNode ? "SUBGRAPH  " + node.getDisplayName() : node.getDisplayName();
    }

    public static boolean isCompactRerouteNode(INode node) {
        if (node == null || !NodeRenderConstants.REROUTE_NODE_TYPE_ID.equalsIgnoreCase(node.getTypeId())) {
            return false;
        }
        if (node instanceof RelayNode relay) {
            String label = relay.getShortTagLabel();
            return label == null || label.isBlank();
        }
        return true;
    }

    public static Result compute(
            INode node,
            NodeDisplayMode mode,
            List<IPort> visibleInputs,
            List<IPort> visibleOutputs,
            TextWidthMeasurer measurer,
            float baseTextLineHeight,
            float baseItemSpacingY) {
        return compute(node, mode, visibleInputs, visibleOutputs, measurer,
                baseTextLineHeight, baseItemSpacingY, NodeVisualLayout::metricsFromNode);
    }

    public static Result compute(
            INode node,
            NodeDisplayMode mode,
            List<IPort> visibleInputs,
            List<IPort> visibleOutputs,
            TextWidthMeasurer measurer,
            float baseTextLineHeight,
            float baseItemSpacingY,
            CustomUIMetricsProvider customUIMetricsProvider) {

        NodeDisplayMode resolvedMode = mode != null ? mode : NodeDisplayMode.TEXT_ONLY;
        TextWidthMeasurer text = measurer != null ? measurer : ignored -> 0f;
        List<IPort> inputs = visibleInputs != null ? visibleInputs : List.of();
        List<IPort> outputs = visibleOutputs != null ? visibleOutputs : List.of();

        if (isCompactRerouteNode(node)) {
            return new Result(
                    NodeRenderConstants.REROUTE_NODE_WIDTH_UNSCALED,
                    NodeRenderConstants.REROUTE_NODE_HEIGHT_UNSCALED,
                    0f,
                    false,
                    false,
                    0f,
                    0f,
                    0f,
                    0f,
                    "",
                    0f,
                    0f,
                    0f,
                    emptyLabels(inputs.size()),
                    emptyLabels(outputs.size()),
                    false,
                    0f,
                    0f,
                    true
            );
        }

        List<String> inputLabels = labelsFor(resolvedMode, inputs);
        List<String> outputLabels = labelsFor(resolvedMode, outputs);

        float portsRegionHeight = portsRegionHeight(inputs.size(), outputs.size(), baseTextLineHeight, baseItemSpacingY);

        Result base;
        if (resolvedMode == NodeDisplayMode.ICON_ONLY) {
            base = computeIconOnly(node, inputs, outputs, inputLabels, outputLabels, text,
                    baseTextLineHeight, portsRegionHeight);
        } else {
            base = computeTextOnly(node, inputs, outputs, inputLabels, outputLabels, text,
                    baseTextLineHeight, portsRegionHeight);
        }
        return applyCustomUIMetrics(base, node, customUIMetricsProvider);
    }

    private static Result applyCustomUIMetrics(
            Result base,
            INode node,
            CustomUIMetricsProvider customUIMetricsProvider) {
        if (customUIMetricsProvider == null || base.compactReroute()) {
            return base;
        }
        CustomUIMetrics metrics = customUIMetricsProvider.metricsFor(node);
        if (!metrics.enabled() || metrics.height() <= 0f) {
            return base;
        }

        float width = base.width();
        if (metrics.minWidth() > 0f) {
            width = Math.max(width, metrics.minWidth() + 2f * NodeRenderConstants.NODE_HORIZONTAL_PADDING);
        }
        float regionTop = base.height();
        // Bottom inset lives inside customUIHeight via BaseCustomUINode.getCustomUIClipSlack()
        // (same as left/right NODE_HORIZONTAL_PADDING). Do not add a second chrome band here.
        float height = base.height() + metrics.height();

        return new Result(
                width,
                height,
                base.headerHeight(),
                base.showTitle(),
                base.showIcon(),
                base.iconSize(),
                base.iconLeft(),
                base.iconTop(),
                base.captionTop(),
                base.caption(),
                base.captionMaxWidth(),
                base.portsRegionHeight(),
                base.portRegionTop(),
                base.inputLabels(),
                base.outputLabels(),
                true,
                metrics.height(),
                regionTop,
                false
        );
    }

    private static Result computeTextOnly(
            INode node,
            List<IPort> inputs,
            List<IPort> outputs,
            List<String> inputLabels,
            List<String> outputLabels,
            TextWidthMeasurer text,
            float baseTextLineHeight,
            float portsRegionHeight) {

        float headerHeight = baseTextLineHeight + 2f * NodeRenderConstants.NODE_VERTICAL_PADDING;
        float titleW = text.measure(nodeTitle(node));
        float portsContentW = portsContentWidth(inputLabels, outputLabels, text);
        float contentW = Math.max(titleW, portsContentW);
        float width = Math.max(
                NodeRenderConstants.TEXT_ONLY_MIN_NODE_WIDTH_UNSCALED,
                contentW + 2f * NodeRenderConstants.NODE_HORIZONTAL_PADDING);

        float height = headerHeight;
        if (portsRegionHeight > 0f) {
            height += portsRegionHeight + NodeRenderConstants.NODE_VERTICAL_PADDING;
        }

        float portRegionTop = headerHeight + NodeRenderConstants.NODE_VERTICAL_PADDING / 2f;

        return new Result(
                width,
                height,
                headerHeight,
                true,
                false,
                0f,
                0f,
                0f,
                0f,
                "",
                0f,
                portsRegionHeight,
                portRegionTop,
                inputLabels,
                outputLabels,
                false,
                0f,
                0f,
                false
        );
    }

    private static Result computeIconOnly(
            INode node,
            List<IPort> inputs,
            List<IPort> outputs,
            List<String> inputLabels,
            List<String> outputLabels,
            TextWidthMeasurer text,
            float baseTextLineHeight,
            float portsRegionHeight) {

        float headerHeight = NodeRenderConstants.ICON_ONLY_HEADER_HEIGHT_UNSCALED;
        float iconSize = NodeRenderConstants.ICON_ONLY_ICON_SIZE_UNSCALED;
        String caption = node != null && node.getDisplayName() != null ? node.getDisplayName() : "";
        float letterColW = maxLabelWidth(inputLabels, outputLabels, text);

        float inputCol = 0f;
        if (!inputs.isEmpty()) {
            inputCol = NodeRenderConstants.PORT_RADIUS_UNSCALED * 2f
                    + NodeRenderConstants.PORT_CIRCLE_TO_TEXT_PADDING
                    + letterColW;
        }
        float outputCol = 0f;
        if (!outputs.isEmpty()) {
            outputCol = letterColW
                    + NodeRenderConstants.PORT_CIRCLE_TO_TEXT_PADDING
                    + NodeRenderConstants.PORT_RADIUS_UNSCALED * 2f;
        }
        float centerGap = NodeRenderConstants.INTER_PORT_COLUMN_PADDING;
        if (!inputs.isEmpty() && !outputs.isEmpty()) {
            centerGap = Math.max(centerGap, iconSize);
        } else {
            centerGap = Math.max(NodeRenderConstants.INTER_PORT_COLUMN_PADDING, iconSize);
        }

        float portsContentW = inputCol + ((!inputs.isEmpty() && !outputs.isEmpty()) ? NodeRenderConstants.INTER_PORT_COLUMN_PADDING : 0f) + outputCol;
        // Prefer enough room for the icon between port columns. Caption truncates into that band
        // and must not drive node width (that caused side overlap with port letters).
        float iconAwarePortsW = inputCol + centerGap + outputCol;
        float contentW = Math.max(Math.max(portsContentW, iconAwarePortsW), iconSize);
        float width = contentW + 2f * NodeRenderConstants.NODE_HORIZONTAL_PADDING;
        width = Math.max(NodeRenderConstants.ICON_ONLY_MIN_NODE_WIDTH_UNSCALED, width);
        width = Math.min(NodeRenderConstants.ICON_ONLY_MAX_NODE_WIDTH_UNSCALED, width);

        float iconBlockHeight = iconSize
                + NodeRenderConstants.NODE_VERTICAL_PADDING
                + baseTextLineHeight
                + NodeRenderConstants.NODE_VERTICAL_PADDING;
        float bodyHeight = Math.max(portsRegionHeight, iconBlockHeight);
        float height = headerHeight + bodyHeight + NodeRenderConstants.NODE_VERTICAL_PADDING;

        float iconLeft = (width - iconSize) / 2f;
        float iconTop = headerHeight + Math.max(0f, (bodyHeight - iconBlockHeight) / 2f)
                + NodeRenderConstants.NODE_VERTICAL_PADDING * 0.5f;
        float captionTop = iconTop + iconSize + NodeRenderConstants.NODE_VERTICAL_PADDING * 0.5f;
        float portRegionTop = headerHeight + NodeRenderConstants.NODE_VERTICAL_PADDING / 2f;
        // Keep caption strictly between port-letter columns (small gap so it does not touch letters).
        float captionSideGap = 2f;
        float captionMaxWidth = Math.max(8f, width - inputCol - outputCol - 2f * captionSideGap);

        return new Result(
                width,
                height,
                headerHeight,
                false,
                true,
                iconSize,
                iconLeft,
                iconTop,
                captionTop,
                caption,
                captionMaxWidth,
                portsRegionHeight,
                portRegionTop,
                inputLabels,
                outputLabels,
                false,
                0f,
                0f,
                false
        );
    }

    private static float portsRegionHeight(int inputCount, int outputCount, float lineH, float itemSpacingY) {
        float spacing = itemSpacingY * 0.8f;
        float inH = inputCount > 0 ? inputCount * lineH + Math.max(0, inputCount - 1) * spacing : 0f;
        float outH = outputCount > 0 ? outputCount * lineH + Math.max(0, outputCount - 1) * spacing : 0f;
        return Math.max(inH, outH);
    }

    private static float portsContentWidth(List<String> inputLabels, List<String> outputLabels, TextWidthMeasurer text) {
        float maxIn = 0f;
        for (String label : inputLabels) {
            maxIn = Math.max(maxIn, text.measure(label));
        }
        float maxOut = 0f;
        for (String label : outputLabels) {
            maxOut = Math.max(maxOut, text.measure(label));
        }
        float width = 0f;
        boolean hasIn = !inputLabels.isEmpty();
        boolean hasOut = !outputLabels.isEmpty();
        if (hasIn) {
            width += NodeRenderConstants.PORT_RADIUS_UNSCALED * 2f
                    + NodeRenderConstants.PORT_CIRCLE_TO_TEXT_PADDING
                    + maxIn;
        }
        if (hasOut) {
            if (hasIn) {
                width += NodeRenderConstants.INTER_PORT_COLUMN_PADDING;
            }
            width += maxOut
                    + NodeRenderConstants.PORT_CIRCLE_TO_TEXT_PADDING
                    + NodeRenderConstants.PORT_RADIUS_UNSCALED * 2f;
        }
        return width;
    }

    private static float maxLabelWidth(List<String> inputLabels, List<String> outputLabels, TextWidthMeasurer text) {
        float max = text.measure("W");
        for (String label : inputLabels) {
            max = Math.max(max, text.measure(label));
        }
        for (String label : outputLabels) {
            max = Math.max(max, text.measure(label));
        }
        return max;
    }

    private static List<String> labelsFor(NodeDisplayMode mode, List<IPort> ports) {
        if (ports.isEmpty()) {
            return List.of();
        }
        List<String> labels = new ArrayList<>(ports.size());
        for (IPort port : ports) {
            String name = port != null ? port.getDisplayName() : null;
            labels.add(NodePortLabelUtil.portLabel(mode, name));
        }
        return Collections.unmodifiableList(labels);
    }

    private static List<String> emptyLabels(int count) {
        if (count <= 0) {
            return List.of();
        }
        List<String> labels = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            labels.add("");
        }
        return Collections.unmodifiableList(labels);
    }
}
