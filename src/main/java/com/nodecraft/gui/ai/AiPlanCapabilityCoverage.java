package com.nodecraft.gui.ai;

import com.nodecraft.gui.ai.model.AiGraphPlan;
import com.nodecraft.gui.ai.model.AiPlanNode;
import com.nodecraft.nodesystem.semantic.NodeCapability;
import com.nodecraft.nodesystem.semantic.NodeSemanticCatalog;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Prompt / plan capability coverage for graph-expansion decisions.
 *
 * <p>Prompt → required capabilities may use keyword heuristics.
 * Plan → present capabilities come from {@link NodeSemanticCatalog}.</p>
 */
public final class AiPlanCapabilityCoverage {

    /**
     * @deprecated Use {@link NodeCapability}. Kept as a bridge for older call sites.
     */
    @Deprecated
    public static final class Capability {
        private Capability() {
        }

        public static final NodeCapability WALL = NodeCapability.WALL;
        public static final NodeCapability OPENING = NodeCapability.OPENING;
        public static final NodeCapability WINDOW = NodeCapability.WINDOW;
        public static final NodeCapability ROOF = NodeCapability.ROOF;
        public static final NodeCapability ARRAY = NodeCapability.ARRAY;
        public static final NodeCapability BOOLEAN_CUT = NodeCapability.BOOLEAN_CUT;
        public static final NodeCapability MATERIAL = NodeCapability.MATERIAL;
        public static final NodeCapability PREVIEW = NodeCapability.PREVIEW;
        public static final NodeCapability SPHERE = NodeCapability.SPHERE;
        public static final NodeCapability BOX = NodeCapability.BOX;
        public static final NodeCapability CURVE = NodeCapability.CURVE;
        public static final NodeCapability TERRAIN = NodeCapability.TERRAIN;
        public static final NodeCapability SDF = NodeCapability.SDF;
        public static final NodeCapability FIELD = NodeCapability.FIELD;
        public static final NodeCapability WORLD_APPLY = NodeCapability.WORLD_APPLY;
    }

    public record CoverageResult(
            Set<NodeCapability> required,
            Set<NodeCapability> present,
            Set<NodeCapability> missing
    ) {
        public boolean hasMissing() {
            return missing != null && !missing.isEmpty();
        }
    }

    private AiPlanCapabilityCoverage() {
    }

    public static Set<NodeCapability> requiredFromPrompt(String prompt) {
        Set<NodeCapability> required = EnumSet.noneOf(NodeCapability.class);
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
            required.add(NodeCapability.WALL);
        }
        if (window) {
            required.add(NodeCapability.WINDOW);
            required.add(NodeCapability.OPENING);
            if (wall) {
                required.add(NodeCapability.BOOLEAN_CUT);
            }
        }
        if (roof) {
            required.add(NodeCapability.ROOF);
        }
        if (sphere) {
            required.add(NodeCapability.SPHERE);
        }
        if (box) {
            required.add(NodeCapability.BOX);
        }
        if (curve) {
            required.add(NodeCapability.CURVE);
        }
        if (terrain) {
            required.add(NodeCapability.TERRAIN);
        }
        if (sdf) {
            required.add(NodeCapability.SDF);
        }
        if (field) {
            required.add(NodeCapability.FIELD);
        }
        if (material) {
            required.add(NodeCapability.MATERIAL);
        }
        if (array) {
            required.add(NodeCapability.ARRAY);
        }

        if (AiIntentAnalysisService.hasWorldApplyIntent(prompt)) {
            required.add(NodeCapability.WORLD_APPLY);
            required.add(NodeCapability.APPLY);
        }

        // Generation-style requests that mention a structure should end with a preview sink.
        if (!required.isEmpty()
                || containsAny(lower, "生成", "创建", "做一个", "造一个", "generate", "create", "make", "build")) {
            if (!required.contains(NodeCapability.WORLD_APPLY)) {
                required.add(NodeCapability.PREVIEW);
            }
        }

        return required;
    }

    public static Set<NodeCapability> presentFromPlan(AiGraphPlan plan) {
        Set<NodeCapability> present = EnumSet.noneOf(NodeCapability.class);
        if (plan == null || plan.nodes() == null) {
            return present;
        }
        NodeSemanticCatalog catalog = NodeSemanticCatalog.get();
        for (AiPlanNode node : plan.nodes()) {
            if (node == null || node.typeId() == null) {
                continue;
            }
            present.addAll(catalog.capabilities(node.typeId()));
        }
        return present;
    }

    public static CoverageResult analyze(String prompt, AiGraphPlan plan) {
        Set<NodeCapability> required = requiredFromPrompt(prompt);
        Set<NodeCapability> present = presentFromPlan(plan);
        Set<NodeCapability> missing = EnumSet.noneOf(NodeCapability.class);
        for (NodeCapability capability : required) {
            if (!present.contains(capability)) {
                missing.add(capability);
            }
        }
        return new CoverageResult(required, present, missing);
    }

    public static Set<NodeCapability> capabilitiesForTypeId(String typeId) {
        return NodeSemanticCatalog.get().capabilities(typeId);
    }

    public static String formatMissingForHint(Set<NodeCapability> missing) {
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
