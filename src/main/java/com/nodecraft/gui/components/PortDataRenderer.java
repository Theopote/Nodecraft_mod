package com.nodecraft.gui.components;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.util.Vec3;
import imgui.ImGui;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

final class PortDataRenderer {
    static {
        PortDataRendererRegistry.registerPredicate(PortDataRenderer::isBlockInfoLikeStatic, (renderer, value) -> renderer.renderBlockInfo(value));
        PortDataRendererRegistry.registerPredicate(PortDataRenderer::isMinecraftBlockLikeStatic, (renderer, value) -> renderer.renderMinecraftBlock(value));
        PortDataRendererRegistry.registerPredicate(PortDataRenderer::isItemStackLikeStatic, (renderer, value) -> renderer.renderItemStack(value));
        PortDataRendererRegistry.registerPredicate(PortDataRenderer::isRegionLikeStatic, (renderer, value) -> renderer.renderRegion(value));
        PortDataRendererRegistry.registerPredicate(PortDataRenderer::isNbtLikeStatic, (renderer, value) -> renderer.renderNBT(value));
    }

    interface Actions {
        void copyToClipboard(String text);

        void highlightPoint(Vec3 point);

        void highlightPoints(List<?> points);

        void highlightRegion(Object region);
    }

    private final Actions actions;

    PortDataRenderer(Actions actions) {
        this.actions = actions;
    }

    void renderPortData(Object value, String label) {
        if (value == null) {
            ImGui.textDisabled("(empty)");
            return;
        }

        String treeNodeId = "portData_" + label + "_" + value.hashCode();
        if (ImGui.treeNodeEx(treeNodeId, imgui.flag.ImGuiTreeNodeFlags.SpanAvailWidth)) {
            try {
                if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                    ImGui.text(label + ": " + value);
                } else if (value instanceof List<?> list) {
                    renderList(list, label);
                } else if (value instanceof Map<?, ?> map) {
                    renderMap(map, label);
                } else if (value instanceof Vec3 vec) {
                    renderVec3(vec, label);
                } else if (PortDataRendererRegistry.render(this, value)) {
                    // Rendered by typed/custom registry entry.
                } else {
                    ImGui.text(label + ": " + value);
                }
            } catch (Exception e) {
                ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "Failed to render port data: " + e.getMessage());
            } finally {
                ImGui.treePop();
            }
        }
    }

    void renderList(List<?> list, String label) {
        int size = list.size();
        if (ImGui.treeNode(label + ": List (" + size + " items)")) {
            int displayLimit = Math.min(size, 10);

            if (!list.isEmpty()) {
                for (int i = 0; i < displayLimit; i++) {
                    Object item = list.get(i);
                    ImGui.pushID(i);
                    if (item == null) {
                        ImGui.text(String.format("[%d] null", i));
                    } else {
                        renderPortData(item, String.format("[%d]", i));
                    }
                    ImGui.popID();
                }
            }

            if (size > displayLimit) {
                if (ImGui.button("View All " + size + " Items...")) {
                    ImGui.openPopup("Full List: " + label);
                }
            }

            if (!list.isEmpty() && list.getFirst() instanceof Vec3) {
                if (ImGui.button("Preview Point Set")) {
                    actions.highlightPoints(list);
                }
            }

            if (ImGui.beginPopup("Full List: " + label)) {
                ImGui.text("Full List (" + size + " items)");
                ImGui.separator();

                float heightLimit = ImGui.getWindowHeight() * 0.6f;
                float childHeight = Math.min(heightLimit, size * (ImGui.getFontSize() + ImGui.getStyle().getItemSpacingY()) + ImGui.getStyle().getWindowPaddingY() * 2);

                if (ImGui.beginChild("ListContent_" + label, ImGui.getContentRegionAvailX(), childHeight, false, ImGuiWindowFlags.AlwaysVerticalScrollbar)) {
                    for (int i = 0; i < size; i++) {
                        Object item = list.get(i);
                        ImGui.text(String.format("[%d] %s", i, item != null ? item.toString() : "null"));
                    }
                    ImGui.endChild();
                }

                ImGui.separator();
                if (ImGui.button("Close")) {
                    ImGui.closeCurrentPopup();
                }

                ImGui.endPopup();
            }
            ImGui.treePop();
        }
    }

    void renderMap(Map<?, ?> map, String label) {
        int size = map.size();
        if (ImGui.treeNode(label + ": Map (" + size + " entries)")) {
            if (ImGui.beginTable("mapTable_" + label, 2, ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg | ImGuiTableFlags.SizingFixedFit)) {
                ImGui.tableSetupColumn("Key");
                ImGui.tableSetupColumn("Value");
                ImGui.tableHeadersRow();

                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object key = entry.getKey();
                    Object value = entry.getValue();

                    ImGui.tableNextRow();
                    ImGui.tableSetColumnIndex(0);
                    ImGui.text(key != null ? key.toString() : "null");

                    ImGui.tableSetColumnIndex(1);
                    if (value == null) {
                        ImGui.textDisabled("null");
                    } else {
                        renderPortData(value, "");
                    }
                }
                ImGui.endTable();
            }
            ImGui.treePop();
        }
    }

    void renderVec3(Vec3 vec, String label) {
        ImGui.text(String.format("X: %.2f, Y: %.2f, Z: %.2f", vec.getX(), vec.getY(), vec.getZ()));

        if (ImGui.button("Copy To Clipboard")) {
            String vecStr = String.format("%.2f %.2f %.2f", vec.getX(), vec.getY(), vec.getZ());
            actions.copyToClipboard(vecStr);
        }

        ImGui.sameLine();

        if (ImGui.button("Preview Point")) {
            actions.highlightPoint(vec);
        }
    }

    private void renderBlockInfo(Object blockInfo) {
        try {
            Method getIdMethod = blockInfo.getClass().getMethod("getId");
            Method getNameMethod = blockInfo.getClass().getMethod("getName");
            Method getPositionMethod = blockInfo.getClass().getMethod("getPosition");

            Object id = getIdMethod.invoke(blockInfo);
            Object name = getNameMethod.invoke(blockInfo);
            Object position = getPositionMethod.invoke(blockInfo);

            ImGui.text("Block ID: " + id);
            ImGui.text("Name: " + name);
            ImGui.text("Position: " + position);

            if (ImGui.button("Highlight In World") && position instanceof Vec3 pos) {
                actions.highlightPoint(pos);
            }
        } catch (NoSuchMethodException e) {
            ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "BlockInfo is missing required methods (getId/getName/getPosition).");
        } catch (Exception e) {
            ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "Failed to read block info: " + e.getMessage());
        }
    }

    private void renderMinecraftBlock(Object block) {
        try {
            Method getIdMethod = block.getClass().getMethod("getId");
            Method getNameMethod = block.getClass().getMethod("getName");

            Object id = getIdMethod.invoke(block);
            Object name = getNameMethod.invoke(block);

            ImGui.text("Block ID: " + id);
            ImGui.text("Name: " + name);
        } catch (NoSuchMethodException e) {
            ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "MinecraftBlock is missing required methods (getId/getName).");
        } catch (Exception e) {
            ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "Failed to read block info: " + e.getMessage());
        }
    }

    private void renderItemStack(Object itemStack) {
        try {
            Method getItemMethod = itemStack.getClass().getMethod("getItem");
            Method getCountMethod = itemStack.getClass().getMethod("getCount");
            Method getNameMethod = null;
            try {
                getNameMethod = itemStack.getClass().getMethod("getName");
            } catch (NoSuchMethodException ignored) {
            }

            Object item = getItemMethod.invoke(itemStack);
            Object count = getCountMethod.invoke(itemStack);
            Object name = null;
            if (getNameMethod != null) {
                name = getNameMethod.invoke(itemStack);
            }

            ImGui.text("Item: " + (name != null ? name : item));
            ImGui.text("Count: " + count);

            if (ImGui.button("Copy Give Command")) {
                String itemId = (item != null) ? item.toString() : "minecraft:air";
                String command = "/give @p " + itemId + " " + count;
                actions.copyToClipboard(command);
            }
        } catch (NoSuchMethodException e) {
            ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "ItemStack is missing required methods (getItem/getCount).");
        } catch (Exception e) {
            ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "Failed to read item info: " + e.getMessage());
        }
    }

    private void renderRegion(Object region) {
        try {
            Method getMinMethod = region.getClass().getMethod("getMin");
            Method getMaxMethod = region.getClass().getMethod("getMax");
            Method getBlockCountMethod = region.getClass().getMethod("getBlockCount");

            Object min = getMinMethod.invoke(region);
            Object max = getMaxMethod.invoke(region);
            Object blockCount = getBlockCountMethod.invoke(region);

            if (min instanceof Vec3 minVec && max instanceof Vec3 maxVec) {
                ImGui.text(String.format("Min Corner: (%.1f, %.1f, %.1f)", minVec.getX(), minVec.getY(), minVec.getZ()));
                ImGui.text(String.format("Max Corner: (%.1f, %.1f, %.1f)", maxVec.getX(), maxVec.getY(), maxVec.getZ()));
            } else {
                ImGui.text("Min Corner: " + min);
                ImGui.text("Max Corner: " + max);
            }

            ImGui.text("Block Count: " + blockCount);

            if (ImGui.button("Highlight Region")) {
                actions.highlightRegion(region);
            }
        } catch (NoSuchMethodException e) {
            ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "Region is missing required methods (getMin/getMax/getBlockCount).");
        } catch (Exception e) {
            ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "Failed to read region info: " + e.getMessage());
        }
    }

    private void renderNBT(Object nbt) {
        try {
            ImGui.textWrapped(nbt.toString());

            if (ImGui.button("Copy NBT")) {
                actions.copyToClipboard(nbt.toString());
            }
        } catch (Exception e) {
            ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "Failed to read NBT data: " + e.getMessage());
            NodeCraft.LOGGER.debug("Failed to render NBT data", e);
        }
    }

    private static boolean isBlockInfoLikeStatic(Object value) {
        return hasMethods(value, "getId", "getName", "getPosition");
    }

    private static boolean isMinecraftBlockLikeStatic(Object value) {
        return hasMethods(value, "getId", "getName");
    }

    private static boolean isItemStackLikeStatic(Object value) {
        return hasMethods(value, "getItem", "getCount");
    }

    private static boolean isRegionLikeStatic(Object value) {
        return hasMethods(value, "getMin", "getMax", "getBlockCount");
    }

    private static boolean isNbtLikeStatic(Object value) {
        String className = value.getClass().getName();
        return className.endsWith(".nbt.CompoundTag")
            || className.endsWith(".nbt.NbtCompound")
            || className.endsWith(".nbt.CompoundNBT");
    }

    private static boolean hasMethods(Object value, String... methodNames) {
        Class<?> type = value.getClass();
        for (String methodName : methodNames) {
            try {
                type.getMethod(methodName);
            } catch (NoSuchMethodException e) {
                return false;
            }
        }
        return true;
    }
}
