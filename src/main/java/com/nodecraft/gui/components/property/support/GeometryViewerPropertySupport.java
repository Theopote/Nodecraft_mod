package com.nodecraft.gui.components.property.support;

import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.nodes.output.preview.GeometryViewerNode;
import com.nodecraft.nodesystem.preview.PreviewBackend;
import imgui.ImGui;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * GeometryViewer-specific property visibility and editor chrome.
 * <p>
 * See {@code docs/architecture/property-panel-breakup.md} (Phase 5).
 */
public final class GeometryViewerPropertySupport {

    private GeometryViewerPropertySupport() {
    }

    public static boolean shouldDisplayProperty(INode node, PropertyDescriptor prop) {
        if (!(node instanceof GeometryViewerNode geometryViewerNode)) {
            return true;
        }

        boolean isGhostBackend = geometryViewerNode.getPreviewBackend() == PreviewBackend.GHOST;
        GeometryViewerNode.GhostRenderMode mode = geometryViewerNode.getGhostRenderMode();

        if (!isGhostBackend && (
                "previewColor".equals(prop.name)
                        || "transparency".equals(prop.name)
                        || "showOutline".equals(prop.name)
                        || "ghostOutlineColor".equals(prop.name)
                        || "ghostRenderMode".equals(prop.name)
        )) {
            return false;
        }

        if ("ghostRenderMode".equals(prop.name)) {
            return isGhostBackend;
        }
        if ("previewColor".equals(prop.name)) {
            return isGhostBackend && mode != GeometryViewerNode.GhostRenderMode.BLOCK_COLOR;
        }
        if ("transparency".equals(prop.name)) {
            return isGhostBackend;
        }
        if ("showOutline".equals(prop.name)) {
            return isGhostBackend && mode == GeometryViewerNode.GhostRenderMode.SOLID_COLOR;
        }
        if ("ghostOutlineColor".equals(prop.name)) {
            return isGhostBackend && mode == GeometryViewerNode.GhostRenderMode.SOLID_COLOR;
        }
        return true;
    }

    public static boolean isTransparency(INode node, PropertyDescriptor prop) {
        return node instanceof GeometryViewerNode && "transparency".equals(prop.name);
    }

    public static boolean isBlockType(INode node, PropertyDescriptor prop) {
        return node instanceof GeometryViewerNode && "blockType".equals(prop.name);
    }

    public static void renderBlockTypeHint(String rawValue) {
        String value = rawValue != null ? rawValue.trim() : "";
        ImGui.sameLine();
        if (value.isEmpty()) {
            ImGui.textColored(0.95f, 0.8f, 0.35f, 1.0f, "Empty (fallback: minecraft:stone)");
            return;
        }

        if (isValidBlockTypeId(value)) {
            ImGui.textColored(0.35f, 0.85f, 0.45f, 1.0f, "Valid");
        } else {
            ImGui.textColored(0.95f, 0.4f, 0.4f, 1.0f, "Invalid block id");
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Use namespace:path, e.g. minecraft:stone");
            }
        }
    }

    public static boolean isValidBlockTypeId(String value) {
        try {
            Identifier id = Identifier.of(value);
            return Registries.BLOCK.containsId(id);
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * @return GeometryViewer-specific label, or {@code null} to use the generic humanized name
     */
    public static String enumDisplayNameOverride(INode node, PropertyDescriptor prop, Enum<?> value) {
        if (value instanceof PreviewBackend backend) {
            return switch (backend) {
                case GHOST -> "Ghost (default)";
                case TRACKED_WORLD -> "Tracked World (compat)";
            };
        }
        if (!(node instanceof GeometryViewerNode) || !"ghostRenderMode".equals(prop.name)) {
            return null;
        }
        return switch (value.name()) {
            case "BLOCK_COLOR" -> "Block Color";
            case "SOLID_COLOR" -> "Solid Color";
            case "WIREFRAME" -> "Wireframe";
            default -> null;
        };
    }

    /**
     * @return GeometryViewer-specific tooltip, or {@code null} for the generic enum list
     */
    public static String enumTooltipOverride(
            INode node,
            PropertyDescriptor prop,
            String[] names,
            int selectedIndex
    ) {
        if (prop != null && "previewBackend".equals(prop.name)) {
            String current = (selectedIndex >= 0 && selectedIndex < names.length) ? names[selectedIndex] : "";
            String gateHint = com.nodecraft.nodesystem.preview.TrackedWorldCompatGate.isCompatSelectionEnabled()
                    ? "- Compat gate ON (-Dnodecraft.preview.trackedWorldCompat=true)\n"
                    : "- Compat gate OFF: new nodes only offer Ghost; enable with -Dnodecraft.preview.trackedWorldCompat=true\n";
            return "Current: " + current + "\n"
                    + "- Ghost (default): render-only overlay; no world mutation\n"
                    + "- Tracked World (compat): temporary blocks + restore; prefer Bake for permanent edits\n"
                    + gateHint
                    + "See docs/architecture/preview-world-boundary.md";
        }
        if (!(node instanceof GeometryViewerNode) || !"ghostRenderMode".equals(prop.name)) {
            return null;
        }
        String current = (selectedIndex >= 0 && selectedIndex < names.length) ? names[selectedIndex] : "";
        return "Current: " + current + "\n"
                + "- Block Color: use block palette-derived color\n"
                + "- Solid Color: use Preview Color fill (+ optional Outline)\n"
                + "- Wireframe: render edges only";
    }
}
