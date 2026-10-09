package com.nodecraft.gui.ai;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.semantic.NodeSemanticCatalog;
import com.nodecraft.nodesystem.semantic.NodeSemanticDescriptor;
import com.nodecraft.nodesystem.semantic.NodeSemanticEdge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds compact node schema metadata for AI prompt construction.
 */
public final class AiNodeSchemaCatalog {

    static final List<String> DIVERSITY_CATEGORY_PREFIXES = List.of(
            "input.",
            "math.",
            "geometry.",
            "spatial.",
            "pattern.",
            "world.",
            "output."
        );

    private AiNodeSchemaCatalog() {
    }

    public record PortSchema(String id, String displayName, String dataType, boolean required, String description) {
    }

    public record ParamSchema(String name, String valueType) {
    }

    public record RecommendationHint(String nodeId, String fromPort, String toPort, String reason) {
    }

    public record NodeSchema(
            String typeId,
            String displayName,
            String description,
            String category,
            List<PortSchema> inputs,
            List<PortSchema> outputs,
            List<ParamSchema> params,
            List<RecommendationHint> recommendedNext,
            List<RecommendationHint> recommendedUpstream,
            String effect,
            List<String> domains,
            List<String> capabilities
    ) {
        public NodeSchema {
            recommendedNext = recommendedNext == null ? List.of() : List.copyOf(recommendedNext);
            recommendedUpstream = recommendedUpstream == null ? List.of() : List.copyOf(recommendedUpstream);
            domains = domains == null ? List.of() : List.copyOf(domains);
            capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        }

        public NodeSchema(
                String typeId,
                String displayName,
                String description,
                String category,
                List<PortSchema> inputs,
                List<PortSchema> outputs,
                List<ParamSchema> params
        ) {
            this(typeId, displayName, description, category, inputs, outputs, params,
                    List.of(), List.of(), null, List.of(), List.of());
        }

        public NodeSchema(
                String typeId,
                String displayName,
                String description,
                String category,
                List<PortSchema> inputs,
                List<PortSchema> outputs,
                List<ParamSchema> params,
                List<RecommendationHint> recommendedNext,
                List<RecommendationHint> recommendedUpstream
        ) {
            this(typeId, displayName, description, category, inputs, outputs, params,
                    recommendedNext, recommendedUpstream, null, List.of(), List.of());
        }

        public NodeSchema withHints(
                List<RecommendationHint> recommendedNext,
                List<RecommendationHint> recommendedUpstream
        ) {
            return new NodeSchema(
                    typeId,
                    displayName,
                    description,
                    category,
                    inputs,
                    outputs,
                    params,
                    recommendedNext,
                    recommendedUpstream,
                    effect,
                    domains,
                    capabilities
            );
        }
    }

    public static List<NodeSchema> collectAll(NodeRegistry registry) {
        if (registry == null) {
            return List.of();
        }

        long epoch = registry.getIntrospectionEpoch();
        List<NodeSchema> cached = cachedSchemas;
        if (cached != null && cachedEpoch == epoch) {
            return cached;
        }

        synchronized (AiNodeSchemaCatalog.class) {
            if (cachedSchemas != null && cachedEpoch == registry.getIntrospectionEpoch()) {
                return cachedSchemas;
            }
            List<NodeSchema> built = buildAll(registry);
            cachedSchemas = List.copyOf(built);
            cachedEpoch = registry.getIntrospectionEpoch();
            return cachedSchemas;
        }
    }

    /**
     * Clears cached AI schema metadata. Normally driven by {@link NodeRegistry} epoch changes.
     */
    public static void invalidateCache() {
        synchronized (AiNodeSchemaCatalog.class) {
            cachedSchemas = null;
            cachedEpoch = -1L;
        }
    }

    private static volatile List<NodeSchema> cachedSchemas;
    private static volatile long cachedEpoch = -1L;

    private static List<NodeSchema> buildAll(NodeRegistry registry) {
        List<NodeSchema> schemas = new ArrayList<>();
        NodeSemanticCatalog catalog = NodeSemanticCatalog.get();
        catalog.refreshIfNeeded();

        List<String> nodeIds = new ArrayList<>(registry.getAllNodeIds());
        nodeIds.sort(String::compareToIgnoreCase);

        for (String nodeId : nodeIds) {
            NodeInfo info = registry.getNodeInfo(nodeId);
            if (info == null) {
                continue;
            }

            try {
                INode node = registry.createNodeInstance(nodeId);
                List<PortSchema> inputs = convertPorts(node.getInputPorts());
                List<PortSchema> outputs = convertPorts(node.getOutputPorts());
                List<ParamSchema> params = extractParamSchema(node.getNodeState());

                NodeSemanticDescriptor descriptor = catalog.describe(info.getId());
                List<RecommendationHint> next = List.of();
                List<RecommendationHint> upstream = List.of();
                String effect = null;
                List<String> domains = List.of();
                List<String> capabilities = List.of();
                if (descriptor != null) {
                    next = toHints(descriptor.downstream());
                    upstream = toHints(descriptor.upstream());
                    effect = descriptor.effect() == null ? null : descriptor.effect().name();
                    domains = enumNames(descriptor.domains());
                    capabilities = enumNames(descriptor.capabilities());
                }

                schemas.add(new NodeSchema(
                        info.getId(),
                        info.getDisplayName(),
                        info.getDescription(),
                        info.getCategoryId(),
                        inputs,
                        outputs,
                        params,
                        next,
                        upstream,
                        effect,
                        domains,
                        capabilities
                ));
            } catch (Exception ignored) {
                // Skip nodes that cannot be instantiated in current runtime state.
            }
        }
        return schemas;
    }

    private static final int MAX_SCHEMA_HINTS = 6;

    private static List<RecommendationHint> toHints(List<NodeSemanticEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            return List.of();
        }
        // Prefer EXACT, then CATEGORY, then TYPE — keep prompt compact.
        List<NodeSemanticEdge> ordered = new ArrayList<>(edges);
        ordered.sort(Comparator
                .comparingInt((NodeSemanticEdge e) -> e.kind() == null ? 0 : e.kind().ordinal())
                .thenComparingInt(NodeSemanticEdge::priority));
        List<RecommendationHint> hints = new ArrayList<>();
        for (NodeSemanticEdge edge : ordered) {
            if (edge == null || edge.targetNodeId() == null || edge.targetNodeId().isBlank()) {
                continue;
            }
            hints.add(new RecommendationHint(
                    edge.targetNodeId(),
                    edge.sourcePortId(),
                    edge.targetPortId(),
                    edge.reason()
            ));
            if (hints.size() >= MAX_SCHEMA_HINTS) {
                break;
            }
        }
        return List.copyOf(hints);
    }

    private static List<String> enumNames(Set<? extends Enum<?>> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>(values.size());
        for (Enum<?> value : values) {
            if (value != null) {
                names.add(value.name());
            }
        }
        names.sort(String::compareTo);
        return List.copyOf(names);
    }

    /** Compact semantic attach for retrieval results when full build cache is stale. */
    public static NodeSchema enrichFromCatalog(NodeSchema schema) {
        if (schema == null || schema.typeId() == null) {
            return schema;
        }
        NodeSemanticCatalog catalog = NodeSemanticCatalog.get();
        NodeSemanticDescriptor descriptor = catalog.describe(schema.typeId());
        if (descriptor != null) {
            return new NodeSchema(
                    schema.typeId(),
                    schema.displayName(),
                    schema.description(),
                    schema.category(),
                    schema.inputs(),
                    schema.outputs(),
                    schema.params(),
                    toHints(descriptor.downstream()),
                    toHints(descriptor.upstream()),
                    descriptor.effect() == null ? schema.effect() : descriptor.effect().name(),
                    enumNames(descriptor.domains()),
                    enumNames(descriptor.capabilities())
            );
        }
        // Registry miss (unit tests / partial bootstrap): still attach derived caps/domains + edges.
        return new NodeSchema(
                schema.typeId(),
                schema.displayName(),
                schema.description(),
                schema.category(),
                schema.inputs(),
                schema.outputs(),
                schema.params(),
                toHints(catalog.effectiveDownstream(schema.typeId())),
                toHints(catalog.effectiveUpstream(schema.typeId())),
                catalog.effect(schema.typeId()).name(),
                enumNames(catalog.domains(schema.typeId())),
                enumNames(catalog.capabilities(schema.typeId()))
        );
    }

    public static List<NodeSchema> selectRelevant(List<NodeSchema> allSchemas, String userPrompt, int limit) {
        return AiSchemaRetrievalService.selectRelevant(allSchemas, userPrompt, limit);
    }

    private static List<PortSchema> convertPorts(List<IPort> ports) {
        List<PortSchema> result = new ArrayList<>();
        if (ports == null) return result;
        for (IPort port : ports) {
            String id = port.getId();
            String displayName = port.getDisplayName();
            
            // Smart labeling: help AI only when the ID is cryptic (e.g., "val1", "arg0", "a", "b").
            // If the ID is descriptive (e.g., "radius", "center"), we omit the display name to save tokens.
            boolean isCryptic = id.length() <= 3 || id.toLowerCase(Locale.ROOT).matches("^(arg|val|in|out|p)[0-9]*$");
            
            result.add(new PortSchema(
                    id,
                    isCryptic ? displayName : null,
                    port.getDataType().getId(),
                    port.isRequired(),
                    null
            ));
        }
        return result;
    }

    private static List<ParamSchema> extractParamSchema(Object nodeState) {
        List<ParamSchema> params = new ArrayList<>();
        if (!(nodeState instanceof Map<?, ?> map)) return params;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String keyText)) continue;
            Object value = entry.getValue();
            String valueType = "any";
            if (value != null) {
                valueType = switch (value) {
                    case Number number -> "number";
                    case Boolean b -> "boolean";
                    case String s -> "string";
                    default -> value.getClass().getSimpleName().toLowerCase(Locale.ROOT);
                };
            }
            params.add(new ParamSchema(keyText, valueType));
        }
        return params;
    }

    static int relevanceScore(
            NodeSchema schema,
            String prompt,
            Set<String> tokens,
            boolean geometryIntent,
            boolean spatialIntent
    ) {
        if (tokens.isEmpty()) {
            return schema.category().startsWith("output.") ? 2 : 1;
        }

        String typeId = safeLower(schema.typeId());
        String displayName = safeLower(schema.displayName());
        String description = safeLower(schema.description());
        String category = safeLower(schema.category());
        String haystack = typeId + " " + displayName + " " + description + " " + category;

        int score = 0;
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }

            if (typeId.contains(token)) {
                score += 5;
            }
            if (displayName.contains(token)) {
                score += 4;
            }
            if (category.contains(token)) {
                score += 4;
            }
            if (description.contains(token)) {
                score += 2;
            }
            if (haystack.contains(token)) {
                score += 1;
            }

            for (PortSchema input : schema.inputs()) {
                if (safeLower(input.id()).contains(token)
                        || safeLower(input.displayName()).contains(token)
                        || safeLower(input.description()).contains(token)
                        || safeLower(input.dataType()).contains(token)) {
                    score += 2;
                }
            }

            for (PortSchema output : schema.outputs()) {
                if (safeLower(output.id()).contains(token)
                        || safeLower(output.displayName()).contains(token)
                        || safeLower(output.description()).contains(token)
                        || safeLower(output.dataType()).contains(token)) {
                    score += 2;
                }
            }

            for (ParamSchema param : schema.params()) {
                if (safeLower(param.name()).contains(token) || safeLower(param.valueType()).contains(token)) {
                    score += 1;
                }
            }
        }

        if (schema.category().startsWith("output.preview.")) {
            score += 1;
        }

        if (AiIntentAnalysisService.hasWorldApplyIntent(prompt)) {
            if (typeId.startsWith("world.write.") || typeId.startsWith("output.execute.")
                    || typeId.contains("bake") || category.contains("bake")) {
                score += 6;
            }
        }

        if (geometryIntent) {
            if (typeId.contains("sphere") || typeId.contains("box") || category.contains("geometry")) score += 6;
        }

        if (spatialIntent) {
            if (typeId.contains("position") || typeId.contains("offset") || typeId.contains("world")) score += 4;
        }

        // Color intent
        if (containsAny(prompt,
            "颜色", "彩色", "红", "绿", "蓝",
            "color", "red", "green", "blue", "rgb", "hsv",
            "цвет", "красный", "зеленый", "синий",
            "color", "rojo", "verde", "azul",
            "cor", "vermelho", "verde", "azul",
            "couleur", "rouge", "vert", "bleu",
            "farbe", "rot", "grün", "blau",
            "色", "赤", "緑", "青",
            "색", "빨강", "초록", "파랑")) {
            if (typeId.contains("color") || category.contains("color")) score += 7;
        }

        // Logic & Math intent
        if (containsAny(prompt,
            "计算", "数学", "加", "减", "乘", "除", "比较",
            "math", "add", "sub", "mul", "div", "logic", "compare",
            "матем", "логик", "слож", "вычит", "умнож", "делен", "сравн",
            "matem", "lógica", "sumar", "restar", "multiplicar", "dividir", "comparar",
            "matem", "logica", "somar", "subtrair", "multiplicar", "dividir", "comparar",
            "math", "logique", "addition", "soustraction", "multiplication", "division", "comparer",
            "mathe", "logik", "addieren", "subtrahieren", "multiplizieren", "dividieren", "vergleichen",
            "数学", "加算", "減算", "乗算", "除算", "比較",
            "수학", "논리", "더하기", "빼기", "곱하기", "나누기", "비교")) {
            if (category.startsWith("math.") || category.startsWith("logic.")) score += 6;
        }

        // Entity & Player intent
        if (containsAny(prompt,
            "玩家", "实体", "生物",
            "player", "entity", "mob", "living",
            "игрок", "сущност", "моб",
            "jugador", "entidad", "mob", "criatura",
            "jogador", "entidade", "mob", "criatura",
            "joueur", "entité", "mob", "créature",
            "spieler", "entität", "mob", "kreatur",
            "プレイヤー", "エンティティ", "モブ",
            "플레이어", "엔티티", "몹")) {
            if (typeId.contains("player") || typeId.contains("entity") || category.contains("input.world")) score += 6;
        }

        return score;
    }

    static Set<String> expandIntentTokens(String prompt, Set<String> baseTokens) {
        Set<String> tokens = new HashSet<>(baseTokens == null ? Set.of() : baseTokens);
        String text = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);

        if (containsAny(text,
            "球", "圆球", "sphere", "ball", "spherical",
            "сфера", "шар",
            "esfera", "bola",
            "esfera", "bola",
            "sphère", "balle",
            "kugel", "sphäre",
            "球体", "スフィア",
            "구", "구체")) {
            tokens.add("sphere");
            tokens.add("geometry");
            tokens.add("mesh");
        }

        if (containsAny(text,
            "玩家", "player", "角色", "entity",
            "игрок", "сущност",
            "jugador", "entidad",
            "jogador", "entidade",
            "joueur", "entité",
            "spieler", "entität",
            "プレイヤー", "エンティティ",
            "플레이어", "엔티티")) {
            tokens.add("player");
            tokens.add("entity");
            tokens.add("position");
        }

        if (containsAny(text,
            "头上", "头顶", "上方", "above", "overhead",
            "над", "сверху",
            "encima", "arriba", "sobre",
            "acima", "sobre",
            "au-dessus", "dessus",
            "oben", "über",
            "上", "上方",
            "위", "위쪽")) {
            tokens.add("offset");
            tokens.add("position");
            tokens.add("translate");
        }

        if (containsAny(text,
            "墙", "墙壁", "墙体", "wall",
            "стена", "muro", "parede", "mur", "wand")) {
            tokens.add("wall");
            tokens.add("slab");
            tokens.add("architectural");
        }

        if (containsAny(text,
            "窗", "窗户", "窗洞", "window", "opening",
            "окно", "ventana", "janela", "fenêtre", "fenster")) {
            tokens.add("window");
            tokens.add("opening");
            tokens.add("array");
        }

        if (containsAny(text,
            "生成", "放置", "输出", "spawn", "produce", "output", "create",
            "создать", "сгенер", "вывод",
            "crear", "generar", "salida",
            "criar", "gerar", "saida",
            "créer", "générer", "sortie",
            "erstellen", "generieren", "ausgabe",
            "生成", "出力", "作成",
            "생성", "출력", "만들")) {
            tokens.add("output");
            tokens.add("preview");
        }

        if (AiIntentAnalysisService.hasWorldApplyIntent(text)) {
            tokens.add("bake");
            tokens.add("apply");
            tokens.add("write");
        }

        return tokens;
    }

    static boolean hasGeometryIntent(String prompt) {
        return containsAny(prompt,
            "几何", "模型", "球", "圆球", "sphere", "mesh", "geometry", "shape",
            "геометр", "сфера", "форма",
            "geometr", "esfera", "forma",
            "geometr", "esfera", "forma",
            "géométr", "sphère", "forme",
            "geometr", "kugel", "form",
            "幾何", "球体", "形状",
            "기하", "구체", "형상");
    }

    static boolean hasSpatialIntent(String prompt) {
        return containsAny(prompt,
            "位置", "坐标", "头上", "头顶", "上方", "position", "offset", "above", "overhead",
            "позици", "координ", "смещ", "над",
            "posición", "coordenad", "desplaz", "encima",
            "posição", "coordenad", "desloc", "acima",
            "position", "coordonnée", "décalage", "au-dessus",
            "position", "koordinate", "versatz", "oben",
            "位置", "座標", "上方",
            "위치", "좌표", "오프셋", "위");
    }

    static boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank() || keywords == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    static String safeLower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    static Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }

        // Unicode-aware tokenization: supports multilingual words (e.g., Latin/CJK/Cyrillic/etc.).
        String[] split = text.split("[^\\p{L}\\p{N}_]+");
        for (String token : split) {
            if (token != null && token.length() >= 2) {
                tokens.add(token.toLowerCase(Locale.ROOT));
            }
        }
        return tokens;
    }
}
