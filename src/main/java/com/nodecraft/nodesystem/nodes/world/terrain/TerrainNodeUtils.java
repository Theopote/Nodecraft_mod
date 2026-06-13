package com.nodecraft.nodesystem.nodes.world.terrain;

final class TerrainNodeUtils {

    static final int DEFAULT_MIN_X = -32;
    static final int DEFAULT_MAX_X = 31;
    static final int DEFAULT_MIN_Z = -32;
    static final int DEFAULT_MAX_Z = 31;
    static final int DEFAULT_MIN_Y = -64;
    static final int DEFAULT_MAX_Y = 320;
    static final int DEFAULT_BASE_Y = 64;

    static final int DEFAULT_MAX_SAMPLES = 10_000;
    static final int DEFAULT_MAX_COLUMNS = 10_000;
    static final int DEFAULT_MAX_PLACEMENTS = 100_000;
    static final int DEFAULT_FILL_DEPTH = 8;

    private TerrainNodeUtils() {
    }

    static int getInputInt(java.util.Map<String, Object> inputValues, String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    static double getInputDouble(java.util.Map<String, Object> inputValues, String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    static boolean getInputBoolean(java.util.Map<String, Object> inputValues, String portId, boolean fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Boolean flag ? flag : fallback;
    }

    static String getInputString(java.util.Map<String, Object> inputValues, String portId, String fallback) {
        Object value = inputValues.get(portId);
        return (value instanceof String text && !text.isBlank()) ? text : fallback;
    }

    static double sanitizeFinite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    static long sampledCount(int minA, int maxA, int minB, int maxB, int step) {
        int stride = Math.max(1, step);
        long countA = ((long) maxA - minA) / stride + 1L;
        long countB = ((long) maxB - minB) / stride + 1L;
        return Math.max(0L, countA) * Math.max(0L, countB);
    }
}
