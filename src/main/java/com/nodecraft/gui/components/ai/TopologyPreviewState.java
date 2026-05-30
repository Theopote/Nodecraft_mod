package com.nodecraft.gui.components.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

final class TopologyPreviewState {

    private String planKey = "";
    private final Map<String, float[]> manualUvByRef = new HashMap<>();
    private String draggingNodeRef = null;
    private float dragOffsetX = 0.0f;
    private float dragOffsetY = 0.0f;

    void reset() {
        planKey = "";
        manualUvByRef.clear();
        draggingNodeRef = null;
        dragOffsetX = 0.0f;
        dragOffsetY = 0.0f;
    }

    void updatePlanKey(String nextPlanKey) {
        String normalized = nextPlanKey == null ? "" : nextPlanKey;
        if (!Objects.equals(planKey, normalized)) {
            planKey = normalized;
            manualUvByRef.clear();
            draggingNodeRef = null;
            dragOffsetX = 0.0f;
            dragOffsetY = 0.0f;
        }
    }

    float[] getManualUv(String nodeRef) {
        return manualUvByRef.get(nodeRef);
    }

    void setManualUv(String nodeRef, float u, float v) {
        manualUvByRef.put(nodeRef, new float[]{u, v});
    }

    String getDraggingNodeRef() {
        return draggingNodeRef;
    }

    void startDragging(String nodeRef, float offsetX, float offsetY) {
        draggingNodeRef = nodeRef;
        dragOffsetX = offsetX;
        dragOffsetY = offsetY;
    }

    void stopDragging() {
        draggingNodeRef = null;
    }

    float getDragOffsetX() {
        return dragOffsetX;
    }

    float getDragOffsetY() {
        return dragOffsetY;
    }
}