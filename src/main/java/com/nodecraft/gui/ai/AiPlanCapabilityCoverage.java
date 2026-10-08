package com.nodecraft.gui.ai;

import com.nodecraft.gui.ai.model.AiGraphPlan;
import com.nodecraft.gui.ai.model.AiPlanNode;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lightweight prompt/plan capability coverage for graph-expansion decisions.
 *
 * <p>P1 uses keyword + typeId/category heuristics. P2 should replace this with
 * NodeSemanticCatalog-derived capabilities.</p>
 */
public final class AiPlanCapabilityCoverage {

    public enum Capability {
        WALL,
        OPENING,
        WINDOW,
        ROOF,
        ARRAY,
        BOOLEAN_CUT,
        MATERIAL,
        PREVIEW,
        SPHERE,
        BOX,
        CURVE,
        TERRAIN,
        SDF,
        FIELD,
        WORLD_APPLY
    }

    public record CoverageResult(
            Set<Capability> required,
            Set<Capability> present,
            Set<Capability> missing
    ) {
        public boolean hasMissing() {
            return missing != null && !missing.isEmpty();
        }
    }

    private AiPlanCapabilityCoverage() {
    }

    public static Set<Capability> requiredFromPrompt(String prompt) {
        Set<Capability> required = EnumSet.noneOf(Capability.class);
        if (prompt == null || prompt.isBlank()) {
            return required;
        }
        String lower = prompt.toLowerCase(Locale.ROOT);

        boolean wall = containsAny(lower, "墙", "墙壁", "墙体", "wall", "стена", "muro", "parede", "mur", "wand");
        boolean window = containsAny(lower,
                "窗", "窗户", "窗洞", "window", "opening", "окно", "ventana", "janela", "fenêtre", "fenster");
        boolean roof = containsAny(lower, "屋顶", "roof", "крыша", "techo", "telhado", "toit", "dach");
        boolean sphere = containsAny(lower, "球", "圆球", "sphere", "ball", "сфера", "esfera", "sphère", "kugel");
        boolean box = containsAny(lower, "盒子", "方块体", "box", "cube", "коробк", "caja", "caixa", "boîte", "würfel");
        boolean curve = containsAny(lower, "曲线", "路径", "curve", "path", "spline", "крив", "camino", "courbe");
        boolean terrain = containsAny(lower, "地形", "terrain", "heightmap", "рельеф", "terreno", "gelände");
        boolean sdf = containsAny(lower, "sdf", "有符号距离", "signed distance");
        boolean field = containsAny(lower, "标量场", "向量场", "scalar field", "vector field");
        boolean material = containsAny(lower, "材质", "调色", "material", "palette", "brick");
        boolean array = containsAny(lower, "阵列", "array", "repeat", "grid");

        if (wall) {
            required.add(Capability.WALL);
        }
        if (window) {
            required.add(Capability.WINDOW);
            required.add(Capability.OPENING);
            if (wall) {
                required.add(Capability.BOOLEAN_CUT);
            }
        }
        if (roof) {
            required.add(Capability.ROOF);
        }
        if (sphere) {
            required.add(Capability.SPHERE);
        }
        if (box) {
            required.add(Capability.BOX);
        }
        if (curve) {
            required.add(Capability.CURVE);
        }
        if (terrain) {
            required.add(Capability.TERRAIN);
        }
        if (sdf) {
            required.add(Capability.SDF);
        }
        if (field) {
            required.add(Capability.FIELD);
        }
        if (material) {
            required.add(Capability.MATERIAL);
        }
        if (array) {
            required.add(Capability.ARRAY);
        }

        if (AiIntentAnalysisService.hasWorldApplyIntent(prompt)) {
            required.add(Capability.WORLD_APPLY);
        }

        // Generation-style requests that mention a structure should end with a preview sink.
        if (!required.isEmpty()
                || containsAny(lower, "生成", "创建", "做一个", "造一个", "generate", "create", "make", "build")) {
            if (!required.contains(Capability.WORLD_APPLY)) {
                required.add(Capability.PREVIEW);
            }
        }

        return required;
    }

    public static Set<Capability> presentFromPlan(AiGraphPlan plan) {
        Set<Capability> present = EnumSet.noneOf(Capability.class);
        if (plan == null || plan.nodes() == null) {
            return present;
        }
        for (AiPlanNode node : plan.nodes()) {
            if (node == null || node.typeId() == null) {
                continue;
            }
            present.addAll(capabilitiesForTypeId(node.typeId()));
        }
        return present;
    }

    public static CoverageResult analyze(String prompt, AiGraphPlan plan) {
        Set<Capability> required = requiredFromPrompt(prompt);
        Set<Capability> present = presentFromPlan(plan);
        Set<Capability> missing = EnumSet.noneOf(Capability.class);
        for (Capability capability : required) {
            if (!present.contains(capability)) {
                missing.add(capability);
            }
        }
        return new CoverageResult(required, present, missing);
    }

    public static Set<Capability> capabilitiesForTypeId(String typeId) {
        Set<Capability> caps = EnumSet.noneOf(Capability.class);
        if (typeId == null || typeId.isBlank()) {
            return caps;
        }
        String id = typeId.toLowerCase(Locale.ROOT);

        if (id.contains("wall_slab") || id.contains("wall_along") || id.contains("wall_with")) {
            caps.add(Capability.WALL);
        }
        if (id.contains("window_array") || id.contains("window")) {
            caps.add(Capability.WINDOW);
            caps.add(Capability.OPENING);
            caps.add(Capability.ARRAY);
        }
        if (id.contains("door_array") || id.contains(".door")) {
            caps.add(Capability.OPENING);
            caps.add(Capability.ARRAY);
        }
        if (id.contains("roof") || id.contains("gable") || id.contains("hip_")) {
            caps.add(Capability.ROOF);
        }
        if (id.contains("difference") || id.contains("boolean")) {
            caps.add(Capability.BOOLEAN_CUT);
        }
        if (id.contains("array") || id.contains("linear_array") || id.contains("polar_array")) {
            caps.add(Capability.ARRAY);
        }
        if (id.startsWith("material.") || id.contains("assign_block") || id.contains("palette")) {
            caps.add(Capability.MATERIAL);
        }
        if (id.startsWith("output.preview.")) {
            caps.add(Capability.PREVIEW);
        }
        if (id.contains("sphere")) {
            caps.add(Capability.SPHERE);
        }
        if (id.contains(".box") || id.endsWith("box") || id.contains("box_from")) {
            caps.add(Capability.BOX);
        }
        if (id.contains("curve") || id.contains("path") || id.contains("spline")) {
            caps.add(Capability.CURVE);
        }
        if (id.contains("terrain") || id.contains("heightmap")) {
            caps.add(Capability.TERRAIN);
        }
        if (id.contains("sdf")) {
            caps.add(Capability.SDF);
        }
        if (id.contains("field") || id.contains("scalar_from") || id.contains("vector_from")) {
            caps.add(Capability.FIELD);
        }
        if (id.startsWith("world.write.") || id.startsWith("output.execute.")) {
            caps.add(Capability.WORLD_APPLY);
        }
        return caps;
    }

    public static String formatMissingForHint(Set<Capability> missing) {
        if (missing == null || missing.isEmpty()) {
            return "";
        }
        List<String> parts = missing.stream().map(Enum::name).sorted().toList();
        return String.join(", ", parts);
    }

    private static boolean containsAny(String lower, String... keywords) {
        if (lower == null || lower.isBlank() || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
