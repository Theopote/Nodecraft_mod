package com.nodecraft.gui.ai.compose;

import com.nodecraft.gui.ai.AiGraphDslSupport;
import com.nodecraft.gui.ai.AiPlanCapabilityCoverage;
import com.nodecraft.gui.ai.AiPlanValidator;
import com.nodecraft.gui.ai.model.AiGraphPlan;
import com.nodecraft.gui.ai.model.AiPlanConnection;
import com.nodecraft.gui.ai.model.AiPlanNode;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.semantic.NodeCapability;
import com.nodecraft.nodesystem.semantic.NodeDomain;
import com.nodecraft.nodesystem.semantic.NodeSemanticCatalog;
import com.nodecraft.nodesystem.semantic.NodeSemanticEdge;
import com.nodecraft.nodesystem.semantic.NodeSemanticEdgeKind;
import com.nodecraft.nodesystem.semantic.NodeSemanticPortKeys;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Deterministic Semantic Composer v1 — completes Preview-first workflows using only
 * {@link NodeSemanticCatalog} edges and explicit {@link TypeConversionRegistry} converters.
 * No hardcoded workflow recipes.
 */
public final class AiSemanticComposer {

    private static final Set<NodeDataType> SILENT_SCALARS = EnumSet.of(
            NodeDataType.DOUBLE,
            NodeDataType.FLOAT,
            NodeDataType.INTEGER,
            NodeDataType.BOOLEAN,
            NodeDataType.STRING
    );

    private static final Set<NodeDomain> SUPPORTED_DOMAINS = EnumSet.of(
            NodeDomain.GEOMETRY,
            NodeDomain.PROFILE,
            NodeDomain.CURVE,
            NodeDomain.ARCHITECTURE,
            NodeDomain.ARRAY,
            NodeDomain.MATERIAL
    );

    /** Capability → preferred seed type ids (seed resolution only — not a workflow table). */
    private static final Map<NodeCapability, List<String>> CAP_SEEDS = Map.ofEntries(
            Map.entry(NodeCapability.SPHERE, List.of("geometry.primitives.sphere")),
            Map.entry(NodeCapability.BOX, List.of("geometry.primitives.box")),
            Map.entry(NodeCapability.WALL, List.of("geometry.architectural_primitives.wall_slab")),
            Map.entry(NodeCapability.WINDOW, List.of("geometry.architectural_primitives.window_array")),
            Map.entry(NodeCapability.OPENING, List.of("geometry.architectural_primitives.window_array")),
            Map.entry(NodeCapability.ARRAY, List.of("geometry.architectural_primitives.window_array")),
            Map.entry(NodeCapability.SWEEP, List.of("geometry.curves.helix", "geometry.profiles.rectangle_profile")),
            Map.entry(NodeCapability.EXTRUDE, List.of("geometry.profiles.rectangle_profile")),
            Map.entry(NodeCapability.CURVE, List.of("geometry.curves.helix", "geometry.curves.arc")),
            Map.entry(NodeCapability.MATERIAL, List.of("material.basic_assignment.assign_block_type"))
    );

    private AiSemanticComposer() {
    }

    public static AiComposeResult composeFromPrompt(String prompt) {
        return compose(AiComposeRequest.fromPrompt(prompt));
    }

    public static AiComposeResult compose(AiComposeRequest request) {
        if (request == null) {
            return AiComposeResult.abstain("Compose request is null.");
        }
        if (isUnsupportedDomainHeavy(request.preferredDomains())
                && request.seedNodeIds().isEmpty()
                && !hasSupportedCapability(request.requiredCapabilities())) {
            return AiComposeResult.abstain("Composer v1 does not freely compose SDF/FIELD/TERRAIN domains.");
        }

        NodeSemanticCatalog catalog = NodeSemanticCatalog.get();
        catalog.refreshIfNeeded();

        List<String> seeds = selectSeeds(request, catalog);
        if (seeds.isEmpty()) {
            return AiComposeResult.abstain("No confident seed node for required capabilities.");
        }

        PlanBuilder builder = new PlanBuilder(request.maxNodes());
        List<String> reasons = new ArrayList<>();
        double cost = 0;

        int branch = 0;
        for (String seed : seeds) {
            if (!builder.addNode(seed, 0, branch * 180f)) {
                return AiComposeResult.abstain("Exceeded maxNodes placing seeds.");
            }
            reasons.add("Seeded " + seed);
            cost += AiComposeCostPolicy.COST_NEW_NODE;
            branch++;
        }

        // Downstream completion from each seed toward Preview Blocks (material chain preferred).
        PathResult best = null;
        for (String seed : seeds) {
            String outPort = primaryOutputPort(seed);
            PathResult candidate = searchDownstream(seed, outPort, request, catalog);
            if (candidate == null) {
                continue;
            }
            if (best == null || preferPath(candidate, best, request.requiredCapabilities())) {
                best = candidate;
            }
        }
        if (best == null) {
            return AiComposeResult.abstain("No semantic Catalog path from seeds to Preview.");
        }
        // Geometry/architecture seeds must land on Preview Blocks (material chain), not a viewer shortcut.
        if (requiresBlocksPreview(seeds) && !pathEndsWithPreferredPreview(best)) {
            return AiComposeResult.abstain("No Catalog path from seeds to Preview Blocks within budget.");
        }
        cost += best.cost();
        reasons.addAll(best.reasons());

        ApplyResult applied = applySteps(builder, best.steps(), catalog, request, reasons);
        if (applied.failMessage() != null) {
            return AiComposeResult.abstain(applied.failMessage());
        }
        cost += applied.extraCost();

        // Fill remaining structural required inputs (join ports) from plan / Catalog upstream.
        String fillFail = fillOpenRequiredInputs(builder, catalog, request, reasons);
        if (fillFail != null) {
            return AiComposeResult.abstain(fillFail);
        }

        if (request.goal() == AiComposeGoal.WORLD_OUTPUT) {
            String applyFail = appendWorldApply(builder, catalog, request, reasons);
            if (applyFail != null) {
                return AiComposeResult.abstain(applyFail);
            }
            cost += AiComposeCostPolicy.COST_NEW_NODE;
        }

        return finalizePlan(builder, request, reasons, cost, catalog);
    }

    // --- Seed selection ---

    static List<String> selectSeeds(AiComposeRequest request, NodeSemanticCatalog catalog) {
        if (!request.seedNodeIds().isEmpty()) {
            return List.copyOf(request.seedNodeIds());
        }

        Set<NodeCapability> caps = request.requiredCapabilities();
        String prompt = request.prompt();
        List<ScoredSeed> scored = new ArrayList<>();

        for (Map.Entry<NodeCapability, List<String>> entry : CAP_SEEDS.entrySet()) {
            if (!caps.contains(entry.getKey())) {
                continue;
            }
            int base = 50 + entry.getValue().size();
            if (entry.getKey() == NodeCapability.WINDOW || entry.getKey() == NodeCapability.OPENING) {
                if (caps.contains(NodeCapability.BOOLEAN_CUT) || caps.contains(NodeCapability.ARRAY)) {
                    base += 40; // prefer window_array for wall+cut
                }
            }
            if (entry.getKey() == NodeCapability.WALL && caps.contains(NodeCapability.WINDOW)) {
                base += 30;
            }
            for (String typeId : entry.getValue()) {
                int score = base + exactEdgeUsefulness(catalog, typeId);
                scored.add(new ScoredSeed(typeId, score));
            }
        }

        // Prompt keyword seeds when caps alone are thin (sweep/helix/extrude/profile).
        if (containsAny(prompt, "sweep", "扫掠", "放样", "helix", "螺旋", "arch", "拱")) {
            scored.add(new ScoredSeed("geometry.curves.helix", 95));
            scored.add(new ScoredSeed("geometry.profiles.rectangle_profile", 90));
        }
        if (containsAny(prompt, "extrude", "拉伸") || (containsAny(prompt, "profile", "轮廓")
                && !containsAny(prompt, "sweep", "扫掠", "helix", "螺旋"))) {
            scored.add(new ScoredSeed("geometry.profiles.rectangle_profile", 88));
        }
        if (caps.contains(NodeCapability.SPHERE) || containsAny(prompt, "球", "sphere")) {
            scored.add(new ScoredSeed("geometry.primitives.sphere", 100));
        }
        if (caps.contains(NodeCapability.BOX) || containsAny(prompt, "盒子", "box", "cube")) {
            scored.add(new ScoredSeed("geometry.primitives.box", 80));
        }
        if (caps.contains(NodeCapability.WALL) || containsAny(prompt, "墙", "wall")) {
            scored.add(new ScoredSeed("geometry.architectural_primitives.wall_slab", 100));
        }
        if (caps.contains(NodeCapability.WINDOW) || containsAny(prompt, "窗", "window")) {
            scored.add(new ScoredSeed("geometry.architectural_primitives.window_array", 110));
        }

        scored.sort(Comparator.comparingInt(ScoredSeed::score).reversed()
                .thenComparing(ScoredSeed::typeId));

        LinkedHashSet<String> seeds = new LinkedHashSet<>();
        for (ScoredSeed seed : scored) {
            seeds.add(seed.typeId());
            if (seeds.size() >= 4) {
                break;
            }
        }
        // Multi-seed join: wall + window together.
        if (caps.contains(NodeCapability.WALL)
                && (caps.contains(NodeCapability.WINDOW) || caps.contains(NodeCapability.OPENING))) {
            seeds.add("geometry.architectural_primitives.wall_slab");
            seeds.add("geometry.architectural_primitives.window_array");
        }
        // Path + profile for sweep-like prompts.
        if (containsAny(prompt, "sweep", "扫掠", "helix", "螺旋", "arch", "拱")
                || caps.contains(NodeCapability.SWEEP)) {
            seeds.add("geometry.curves.helix");
            seeds.add("geometry.profiles.rectangle_profile");
        }
        return List.copyOf(seeds);
    }

    private static int exactEdgeUsefulness(NodeSemanticCatalog catalog, String typeId) {
        int n = 0;
        for (NodeSemanticEdge edge : catalog.exactDownstream(typeId)) {
            if (edge != null) {
                n++;
            }
        }
        return Math.min(n, 10);
    }

    // --- Downstream UCS ---

    private static PathResult searchDownstream(
            String seedTypeId,
            String seedPort,
            AiComposeRequest request,
            NodeSemanticCatalog catalog
    ) {
        PriorityQueue<AiComposeSearchState> queue = new PriorityQueue<>();
        Set<NodeCapability> seedCaps = new HashSet<>(catalog.capabilities(seedTypeId));
        // Path-local cycle guard only — global exploration uses bestCost UCS (not enqueue-time visited).
        LinkedHashSet<String> startPath = new LinkedHashSet<>();
        startPath.add(seedTypeId);
        queue.add(new AiComposeSearchState(
                startPath, seedTypeId, seedPort, seedCaps, Set.of(), 0, List.of()));

        // Key includes continuePort so a cheaper EXACT entry can reopen after an earlier TYPE discovery.
        Map<String, Double> bestCost = new HashMap<>();
        bestCost.put(visitKey(seedTypeId, seedPort, seedPort), 0.0);

        PathResult bestBlocks = null;
        PathResult bestOtherPreview = null;
        int expansions = 0;
        while (!queue.isEmpty() && expansions < 300) {
            expansions++;
            AiComposeSearchState state = queue.poll();
            if (state.typePath().size() > request.maxNodes()) {
                continue;
            }
            if (isPreviewTerminal(state.frontierTypeId())) {
                PathResult found = PathResult.fromState(state);
                if (isPreferredPreview(state.frontierTypeId())) {
                    if (bestBlocks == null || found.cost() < bestBlocks.cost()) {
                        bestBlocks = found;
                    }
                    // Keep searching for a cheaper blocks path, but do not expand past Preview.
                } else if (bestBlocks == null) {
                    if (bestOtherPreview == null || found.cost() < bestOtherPreview.cost()) {
                        bestOtherPreview = found;
                    }
                }
                continue;
            }
            if (isApplyTerminal(state.frontierTypeId())) {
                continue; // Apply appended separately under WORLD_OUTPUT
            }

            List<NodeSemanticEdge> edges = catalog.effectiveDownstream(
                    state.frontierTypeId(), state.frontierPortKey(), null);
            if (edges.isEmpty()) {
                edges = catalog.effectiveDownstream(state.frontierTypeId());
            }

            for (NodeSemanticEdge edge : edges) {
                if (edge == null || edge.targetNodeId() == null) {
                    continue;
                }
                if (!isEdgeAllowed(edge, state.frontierTypeId(), catalog, request.allowWorldWrite())) {
                    continue;
                }
                String nextType = edge.targetNodeId();
                if (isApplyTerminal(nextType) && request.goal() != AiComposeGoal.WORLD_OUTPUT) {
                    continue;
                }
                // Skip early preview_geometry/curves sinks when expanding — bias toward material chain.
                if (isPreviewTerminal(nextType) && !isPreferredPreview(nextType) && bestBlocks == null) {
                    // Still enqueue as fallback terminal (handled when dequeued).
                }
                if (!isEffectAllowed(catalog.effect(nextType), request.allowWorldWrite())) {
                    continue;
                }
                // Do not re-enter a type already on this path (cycle guard).
                if (state.typePath().contains(nextType) && !isPreviewTerminal(nextType)) {
                    continue;
                }

                Set<NodeCapability> nextCovered = EnumSet.noneOf(NodeCapability.class);
                nextCovered.addAll(state.covered());
                nextCovered.addAll(catalog.capabilities(nextType));

                double stepCost = AiComposeCostPolicy.edgeCost(edge.kind())
                        + AiComposeCostPolicy.nodePenalty(catalog.effect(nextType));
                // Soft penalty for non-blocks preview so UCS prefers Voxelize→Assign→PreviewBlocks.
                if (isPreviewTerminal(nextType) && !isPreferredPreview(nextType)) {
                    stepCost += 20;
                }
                // Prefer Assign → Preview Blocks over Voxelize → Preview Blocks shortcuts.
                if (isPreferredPreview(nextType)
                        && !"material.basic_assignment.assign_block_type"
                        .equalsIgnoreCase(state.frontierTypeId())) {
                    stepCost += 15;
                }
                // When BOOLEAN_CUT is required, prefer Difference before Voxelize.
                if (request.requiredCapabilities().contains(NodeCapability.BOOLEAN_CUT)
                        && nextType.contains("voxelize")
                        && state.typePath().stream().noneMatch(t -> t.contains("difference"))) {
                    stepCost += 40;
                }
                // When Sweep is hinted, prefer Sweep / SurfaceStripToBlocks chain.
                if ((request.requiredCapabilities().contains(NodeCapability.SWEEP)
                        || containsAny(request.prompt(), "sweep", "helix", "扫掠", "螺旋", "arch", "拱"))
                        && nextType.contains("voxelize")
                        && state.typePath().stream().noneMatch(t -> t.contains("sweep"))) {
                    stepCost += 25;
                }
                // When Extrude is hinted, prefer Profile→Region→Extrude before viewer/sweep shortcuts.
                boolean extrudeHint = request.requiredCapabilities().contains(NodeCapability.EXTRUDE)
                        || containsAny(request.prompt(), "extrude", "拉伸");
                boolean sweepHint = request.requiredCapabilities().contains(NodeCapability.SWEEP)
                        || containsAny(request.prompt(), "sweep", "helix", "扫掠", "螺旋", "arch", "拱");
                if (extrudeHint && !sweepHint && nextType.contains("sweep")) {
                    stepCost += 50;
                }
                // Prefer Profile→Region→Extrude Region over legacy Extrude Profile shortcut.
                if (extrudeHint && "geometry.solids.extrude".equalsIgnoreCase(nextType)) {
                    stepCost += 25;
                }
                if (extrudeHint && nextType.contains("profile_to_region")) {
                    stepCost = Math.max(0, stepCost - 3);
                }
                if (extrudeHint
                        && isPreviewTerminal(nextType)
                        && state.typePath().stream().noneMatch(t -> t.contains("extrude"))) {
                    stepCost += 30;
                }
                double newCost = state.cost() + stepCost;
                String continuePort = primaryOutputPort(nextType);
                String key = visitKey(nextType, continuePort, edge.sourcePortId());
                Double prevBest = bestCost.get(key);
                if (prevBest != null && prevBest <= newCost) {
                    continue;
                }
                bestCost.put(key, newCost);

                AiComposeSearchState.Step step = new AiComposeSearchState.Step(
                        state.frontierTypeId(),
                        edge.sourcePortId() != null ? edge.sourcePortId() : state.frontierPortKey(),
                        nextType,
                        edge.targetPortId(),
                        edge.reason(),
                        false
                );
                List<AiComposeSearchState.Step> nextSteps = new ArrayList<>(state.steps());
                nextSteps.add(step);
                AiComposeSearchState next = state.withFrontier(
                        nextType, continuePort, nextCovered, Set.of(), newCost, nextSteps);
                queue.add(next);
            }
            // Early exit once a cheap blocks path is found and queue costs cannot beat it.
            if (bestBlocks != null && !queue.isEmpty() && queue.peek().cost() >= bestBlocks.cost()) {
                break;
            }
        }
        return bestBlocks != null ? bestBlocks : bestOtherPreview;
    }

    /**
     * Prefer paths that cover required join caps (BOOLEAN_CUT/SWEEP), then Preview Blocks,
     * then lower Catalog cost.
     */
    private static boolean preferPath(PathResult a, PathResult b, Set<NodeCapability> required) {
        int aCov = pathCapabilityScore(a, required);
        int bCov = pathCapabilityScore(b, required);
        if (aCov != bCov) {
            return aCov > bCov;
        }
        boolean aBlocks = pathEndsWithPreferredPreview(a);
        boolean bBlocks = pathEndsWithPreferredPreview(b);
        if (aBlocks != bBlocks) {
            return aBlocks;
        }
        return a.cost() < b.cost();
    }

    private static int pathCapabilityScore(PathResult path, Set<NodeCapability> required) {
        if (path == null || required == null || required.isEmpty()) {
            return 0;
        }
        Set<String> types = new HashSet<>();
        for (AiComposeSearchState.Step step : path.steps()) {
            types.add(step.fromTypeId());
            types.add(step.toTypeId());
        }
        int score = 0;
        if (required.contains(NodeCapability.BOOLEAN_CUT)
                && types.stream().anyMatch(t -> t != null && t.contains("difference"))) {
            score += 100;
        }
        if (required.contains(NodeCapability.WINDOW) || required.contains(NodeCapability.OPENING)) {
            if (types.stream().anyMatch(t -> t != null && t.contains("difference"))) {
                score += 50;
            }
        }
        if (required.contains(NodeCapability.SWEEP)
                && types.stream().anyMatch(t -> t != null && t.contains("sweep"))) {
            score += 80;
        }
        if (types.stream().anyMatch(t -> t != null && t.contains("profile_to_region"))) {
            score += 45;
        }
        if (types.stream().anyMatch(t -> t != null && t.contains("extrude_region"))) {
            score += 40;
        }
        if (types.stream().anyMatch(t -> t != null && t.contains("sweep"))) {
            score += 30;
        }
        if (types.stream().anyMatch(t -> t != null && t.contains("assign_block_type"))) {
            score += 20;
        }
        return score;
    }

    private static boolean pathEndsWithPreferredPreview(PathResult path) {
        if (path == null || path.steps().isEmpty()) {
            return false;
        }
        return isPreferredPreview(path.steps().getLast().toTypeId());
    }

    private static boolean isPreferredPreview(String typeId) {
        return "output.preview.preview_blocks".equalsIgnoreCase(typeId);
    }

    private static boolean requiresBlocksPreview(List<String> seeds) {
        for (String seed : seeds) {
            if (seed == null) {
                continue;
            }
            if (seed.startsWith("geometry.primitives.")
                    || seed.startsWith("geometry.architectural_")
                    || seed.startsWith("geometry.boolean.")
                    || seed.startsWith("geometry.solids.")
                    || seed.startsWith("geometry.profiles.")
                    || seed.startsWith("geometry.curves.")
                    || seed.startsWith("geometry.voxel.")) {
                return true;
            }
        }
        return false;
    }

    /** Plan-local producer that has Catalog semantic evidence to the consumer port. */
    private static ProducerMatch findSemanticPlanProducer(
            PlanBuilder builder,
            NodeDataType want,
            String targetType,
            String targetPort,
            NodeSemanticCatalog catalog
    ) {
        ProducerMatch best = null;
        int bestCost = Integer.MAX_VALUE;
        for (String typeId : builder.typeIds()) {
            if (typeId.equalsIgnoreCase(targetType)) {
                continue;
            }
            INode node = tryCreate(typeId);
            if (node == null || node.getOutputPorts() == null) {
                continue;
            }
            for (IPort out : node.getOutputPorts()) {
                if (out == null || out.getDataType() == NodeDataType.EXEC) {
                    continue;
                }
                boolean linked = hasSemanticLink(catalog, typeId, out.getId(), targetType, targetPort)
                        || isCatalogUpstreamProducer(catalog, typeId, out.getId(), targetType, targetPort, want);
                if (!linked) {
                    continue;
                }
                TypeConversionRegistry.ConversionPolicy policy =
                        catalog.conversionBetween(out.getDataType(), want);
                if (out.getDataType() != want
                        && policy == TypeConversionRegistry.ConversionPolicy.UNSUPPORTED) {
                    continue;
                }
                int cost = out.getDataType() == want ? 0
                        : policy == TypeConversionRegistry.ConversionPolicy.IMPLICIT_SAFE ? 1
                        : AiComposeCostPolicy.COST_CONVERSION;
                // Prefer exact data-type match on the physical port (e.g. output_box_geometry).
                if (out.getDataType() == want) {
                    cost = Math.max(0, cost - 1);
                }
                if (cost < bestCost) {
                    bestCost = cost;
                    best = new ProducerMatch(builder.refForType(typeId), typeId, out.getId());
                }
            }
        }
        return best;
    }

    private static ApplyResult applySteps(
            PlanBuilder builder,
            List<AiComposeSearchState.Step> steps,
            NodeSemanticCatalog catalog,
            AiComposeRequest request,
            List<String> reasons
    ) {
        double extra = 0;
        for (AiComposeSearchState.Step step : steps) {
            String fromType = step.fromTypeId();
            String toType = step.toTypeId();
            String fromRef = builder.refForType(fromType);
            if (fromRef == null) {
                return ApplyResult.fail("Missing source node in plan: " + fromType);
            }
            if (!builder.hasType(toType)) {
                if (!builder.addNode(toType, builder.depth() + 1, 0)) {
                    return ApplyResult.fail("Exceeded maxNodes applying " + toType);
                }
                extra += AiComposeCostPolicy.COST_NEW_NODE;
            }
            if (!isEffectAllowed(catalog.effect(toType), request.allowWorldWrite())) {
                return ApplyResult.fail("Blocked effect for " + toType);
            }
            String toRef = builder.refForType(toType);
            String outPort = step.fromPortId() != null ? step.fromPortId() : primaryOutputPort(fromType);
            String inPort = step.toPortId() != null ? step.toPortId() : findStructuralInput(toType, null);
            if (outPort == null || inPort == null) {
                return ApplyResult.fail("Unresolved ports for " + fromType + " → " + toType);
            }
            WireResult wire = wireWithConversion(builder, fromRef, outPort, toRef, inPort, catalog, request, reasons);
            if (wire.failed()) {
                return ApplyResult.fail(wire.message());
            }
            extra += wire.extraCost();
            if (step.reason() != null && !step.reason().isBlank()) {
                reasons.add(step.reason());
            }
        }
        return ApplyResult.ok(extra);
    }

    // --- Upstream / join fill ---

    private static final int UPSTREAM_SPAWN_DEPTH = 3;

    private static String fillOpenRequiredInputs(
            PlanBuilder builder,
            NodeSemanticCatalog catalog,
            AiComposeRequest request,
            List<String> reasons
    ) {
        // Plan-local Catalog joins first, then Catalog upstream spawn (incl. BOX_FACE chain).
        for (int pass = 0; pass < 8; pass++) {
            boolean progressed = false;
            for (String typeId : List.copyOf(builder.typeIds())) {
                String ref = builder.refForType(typeId);
                INode node = tryCreate(typeId);
                if (node == null || node.getInputPorts() == null) {
                    continue;
                }
                for (IPort port : node.getInputPorts()) {
                    if (port == null || port.getDataType() == NodeDataType.EXEC) {
                        continue;
                    }
                    if (!port.isRequired()) {
                        continue;
                    }
                    if (builder.isConnected(ref, port.getId())) {
                        continue;
                    }
                    if (hasSiblingStructuralConnection(builder, ref, typeId, port)) {
                        continue;
                    }
                    ProducerMatch planMatch = findSemanticPlanProducer(
                            builder, port.getDataType(), typeId, port.getId(), catalog);
                    if (planMatch != null) {
                        WireResult wire = wireWithConversion(
                                builder, planMatch.ref(), planMatch.portId(), ref, port.getId(),
                                catalog, request, reasons);
                        if (!wire.failed()) {
                            reasons.add("Joined " + planMatch.typeId() + "." + planMatch.portId()
                                    + " → " + typeId + "." + port.getId());
                            progressed = true;
                            continue;
                        }
                    }
                    if (spawnCatalogUpstream(
                            builder, typeId, port, catalog, request, reasons, UPSTREAM_SPAWN_DEPTH)) {
                        progressed = true;
                    }
                }
            }
            if (!progressed) {
                break;
            }
        }
        for (String typeId : builder.typeIds()) {
            String ref = builder.refForType(typeId);
            INode node = tryCreate(typeId);
            if (node == null || node.getInputPorts() == null) {
                continue;
            }
            for (IPort port : node.getInputPorts()) {
                if (port == null || port.getDataType() == NodeDataType.EXEC || !port.isRequired()) {
                    continue;
                }
                if (builder.isConnected(ref, port.getId())) {
                    continue;
                }
                if (hasSiblingStructuralConnection(builder, ref, typeId, port)) {
                    continue;
                }
                return "Required input unsatisfied: " + typeId + "." + port.getId();
            }
        }
        return null;
    }

    /**
     * Spawn cheapest effect-gated Catalog upstream producer for an open required port,
     * then lightly recurse so chains like Box → GetBoxFace → Wall.input_face can complete.
     */
    private static boolean spawnCatalogUpstream(
            PlanBuilder builder,
            String consumerType,
            IPort port,
            NodeSemanticCatalog catalog,
            AiComposeRequest request,
            List<String> reasons,
            int depthLeft
    ) {
        if (depthLeft < 0 || port == null || consumerType == null) {
            return false;
        }
        String consumerRef = builder.refForType(consumerType);
        if (consumerRef == null || builder.isConnected(consumerRef, port.getId())) {
            return false;
        }
        NodeSemanticEdge best = pickCheapestUpstream(
                catalog.effectiveUpstream(consumerType, port.getId(), port.getDataType()),
                catalog,
                request);
        if (best == null) {
            // Prefer EXACT/CATEGORY; allow TYPE-only when nothing better exists.
            best = pickCheapestUpstream(
                    catalog.effectiveUpstream(consumerType, null, port.getDataType()),
                    catalog,
                    request);
        }
        if (best == null || best.targetNodeId() == null) {
            return false;
        }
        String producerType = best.targetNodeId();
        if (producerType.equalsIgnoreCase(consumerType) || builder.typeIds().stream()
                .anyMatch(t -> t.equalsIgnoreCase(producerType) && wouldCreateCycle(builder, producerType, consumerType))) {
            // Already in plan is fine; only reject self-target.
            if (producerType.equalsIgnoreCase(consumerType)) {
                return false;
            }
        }
        if (isPreviewTerminal(producerType) || isApplyTerminal(producerType)) {
            return false;
        }
        if (!isEffectAllowed(catalog.effect(producerType), request.allowWorldWrite())) {
            return false;
        }
        boolean newlyAdded = !builder.hasType(producerType);
        if (newlyAdded && !builder.addNode(producerType, Math.max(0, builder.depth() - 1), -120f)) {
            return false;
        }
        String producerRef = builder.refForType(producerType);
        String producerOut = NodeSemanticPortKeys.physicalBase(
                best.sourcePortId() != null ? best.sourcePortId() : primaryOutputPort(producerType));
        if (producerOut == null || producerOut.isBlank()) {
            producerOut = primaryOutputPort(producerType);
        }
        // Prefer a type-compatible output when Catalog connect port is missing / synthetic-only.
        if (portDataType(producerType, producerOut, true) != port.getDataType()) {
            String typed = findCompatibleOutput(producerType, port.getDataType());
            if (typed != null) {
                producerOut = typed;
            }
        }
        String consumerIn = best.targetPortId() != null ? best.targetPortId() : port.getId();
        WireResult wire = wireWithConversion(
                builder, producerRef, producerOut, consumerRef, consumerIn, catalog, request, reasons);
        if (wire.failed()) {
            return false;
        }
        reasons.add((newlyAdded ? "Spawned upstream " : "Linked upstream ")
                + producerType + "." + producerOut + " → " + consumerType + "." + consumerIn);
        // Recurse lightly on the producer's remaining required inputs.
        INode producer = tryCreate(producerType);
        if (producer != null && producer.getInputPorts() != null && depthLeft > 0) {
            for (IPort upPort : producer.getInputPorts()) {
                if (upPort == null || upPort.getDataType() == NodeDataType.EXEC || !upPort.isRequired()) {
                    continue;
                }
                if (builder.isConnected(producerRef, upPort.getId())) {
                    continue;
                }
                if (hasSiblingStructuralConnection(builder, producerRef, producerType, upPort)) {
                    continue;
                }
                ProducerMatch planMatch = findSemanticPlanProducer(
                        builder, upPort.getDataType(), producerType, upPort.getId(), catalog);
                if (planMatch != null) {
                    WireResult join = wireWithConversion(
                            builder, planMatch.ref(), planMatch.portId(), producerRef, upPort.getId(),
                            catalog, request, reasons);
                    if (!join.failed()) {
                        reasons.add("Joined " + planMatch.typeId() + "." + planMatch.portId()
                                + " → " + producerType + "." + upPort.getId());
                        continue;
                    }
                }
                spawnCatalogUpstream(
                        builder, producerType, upPort, catalog, request, reasons, depthLeft - 1);
            }
        }
        return builder.isConnected(consumerRef, port.getId());
    }

    private static boolean wouldCreateCycle(PlanBuilder builder, String fromType, String toType) {
        // v1 one-instance-per-type: reject only when wiring would connect a node to itself.
        return fromType != null && fromType.equalsIgnoreCase(toType);
    }

    private static NodeSemanticEdge pickCheapestUpstream(
            List<NodeSemanticEdge> edges,
            NodeSemanticCatalog catalog,
            AiComposeRequest request
    ) {
        if (edges == null || edges.isEmpty()) {
            return null;
        }
        NodeSemanticEdge best = null;
        int bestScore = Integer.MAX_VALUE;
        for (NodeSemanticEdge edge : edges) {
            if (edge == null || edge.targetNodeId() == null) {
                continue;
            }
            if (!isEffectAllowed(catalog.effect(edge.targetNodeId()), request.allowWorldWrite())) {
                continue;
            }
            if (isPreviewTerminal(edge.targetNodeId()) || isApplyTerminal(edge.targetNodeId())) {
                continue;
            }
            int score = AiComposeCostPolicy.edgeCost(edge.kind()) * 100
                    + Math.max(0, edge.priority())
                    + AiComposeCostPolicy.nodePenalty(catalog.effect(edge.targetNodeId()));
            // Prefer EXACT/CATEGORY over TYPE when both exist.
            if (edge.kind() == NodeSemanticEdgeKind.TYPE) {
                score += 50;
            }
            if (score < bestScore) {
                bestScore = score;
                best = edge;
            }
        }
        return best;
    }

    private static boolean hasSiblingStructuralConnection(
            PlanBuilder builder,
            String ref,
            String typeId,
            IPort current
    ) {
        // Only Assign-like multi-alt materialization ports are siblings. Difference
        // base+cutter are both GEOMETRY but both required — never treat as alternatives.
        if (!isMaterializationInput(current.getId())) {
            return false;
        }
        INode node = tryCreate(typeId);
        if (node == null || node.getInputPorts() == null) {
            return false;
        }
        for (IPort port : node.getInputPorts()) {
            if (port == null || port.getId().equals(current.getId())) {
                continue;
            }
            if (builder.isConnected(ref, port.getId()) && isMaterializationInput(port.getId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMaterializationInput(String portId) {
        if (portId == null) {
            return false;
        }
        String p = portId.toLowerCase(Locale.ROOT);
        return p.contains("coordinate") || p.contains("placement") || p.contains("geometry")
                || p.contains("blocks");
    }

    private static boolean hasSemanticLink(
            NodeSemanticCatalog catalog,
            String fromType,
            String fromPort,
            String toType,
            String toPort
    ) {
        for (NodeSemanticEdge edge : catalog.effectiveDownstream(fromType, fromPort, null)) {
            if (edge == null) {
                continue;
            }
            if (!toType.equalsIgnoreCase(edge.targetNodeId())) {
                continue;
            }
            // Plan-local joins may use TYPE edges (e.g. polygon_profile → sweep); UCS still costs them higher.
            if (toPort == null || edge.targetPortId() == null || toPort.equals(edge.targetPortId())) {
                return true;
            }
        }
        // Physical port queries miss synthetic keys (output_face:vertical) — fall back to full downstream.
        if (fromPort != null) {
            for (NodeSemanticEdge edge : catalog.effectiveDownstream(fromType)) {
                if (edge == null || !toType.equalsIgnoreCase(edge.targetNodeId())) {
                    continue;
                }
                String edgeSrc = NodeSemanticPortKeys.physicalBase(edge.sourcePortId());
                if (fromPort.equals(edgeSrc) || fromPort.equals(edge.sourcePortId())) {
                    if (toPort == null || edge.targetPortId() == null || toPort.equals(edge.targetPortId())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** True when Catalog upstream of the consumer lists this producer (EXACT/CATEGORY/TYPE). */
    private static boolean isCatalogUpstreamProducer(
            NodeSemanticCatalog catalog,
            String producerType,
            String producerPort,
            String consumerType,
            String consumerPort,
            NodeDataType want
    ) {
        for (NodeSemanticEdge edge : catalog.effectiveUpstream(consumerType, consumerPort, want)) {
            if (edge == null || !producerType.equalsIgnoreCase(edge.targetNodeId())) {
                continue;
            }
            String edgeOut = NodeSemanticPortKeys.physicalBase(edge.sourcePortId());
            if (producerPort == null
                    || edge.sourcePortId() == null
                    || producerPort.equals(edge.sourcePortId())
                    || producerPort.equals(edgeOut)) {
                return true;
            }
        }
        return false;
    }

    private static String appendWorldApply(
            PlanBuilder builder,
            NodeSemanticCatalog catalog,
            AiComposeRequest request,
            List<String> reasons
    ) {
        String assignType = "material.basic_assignment.assign_block_type";
        String applyType = "output.execute.apply_changes";
        String assignRef = builder.refForType(assignType);
        if (assignRef == null) {
            return "WORLD_OUTPUT requires Assign Block Type before Apply.";
        }
        if (!isEffectAllowed(catalog.effect(applyType), true)) {
            return "Apply Changes effect blocked.";
        }
        if (!builder.addNode(applyType, builder.depth() + 1, 200f)) {
            return "Exceeded maxNodes appending Apply Changes.";
        }
        String applyRef = builder.refForType(applyType);
        String placements = findOutputPort(assignType, "output_placements", null);
        String inPort = findInputPort(applyType, "input_block_placements", null);
        WireResult wire = wireWithConversion(
                builder, assignRef, placements, applyRef, inPort, catalog, request, reasons);
        if (wire.failed()) {
            return wire.message();
        }
        reasons.add("Added Apply Changes because world-write intent was explicit");
        return null;
    }

    // --- Conversion wiring ---

    private static WireResult wireWithConversion(
            PlanBuilder builder,
            String sourceRef,
            String sourcePortId,
            String targetRef,
            String targetPortId,
            NodeSemanticCatalog catalog,
            AiComposeRequest request,
            List<String> reasons
    ) {
        if (sourcePortId == null || targetPortId == null) {
            return WireResult.fail("Missing port ids for wiring.");
        }
        if (builder.hasConnection(sourceRef, sourcePortId, targetRef, targetPortId)) {
            return WireResult.ok(0);
        }
        NodeDataType outType = portDataType(builder.typeForRef(sourceRef), sourcePortId, true);
        NodeDataType inType = portDataType(builder.typeForRef(targetRef), targetPortId, false);
        if (outType == null || inType == null || outType == inType) {
            builder.connect(sourceRef, sourcePortId, targetRef, targetPortId);
            return WireResult.ok(0);
        }
        TypeConversionRegistry.ConversionPolicy policy = catalog.conversionBetween(outType, inType);
        if (policy == TypeConversionRegistry.ConversionPolicy.IMPLICIT_SAFE) {
            builder.connect(sourceRef, sourcePortId, targetRef, targetPortId);
            return WireResult.ok(0);
        }
        if (policy == TypeConversionRegistry.ConversionPolicy.UNSUPPORTED) {
            return WireResult.fail("Unsupported conversion " + outType + " → " + inType);
        }
        TypeConversionRegistry.ConversionSuggestion suggestion = catalog.suggestedConversion(outType, inType);
        if (suggestion == null || suggestion.nodeId() == null) {
            return WireResult.fail("No converter for " + outType + " → " + inType);
        }
        if (!isEffectAllowed(catalog.effect(suggestion.nodeId()), request.allowWorldWrite())) {
            return WireResult.fail("Converter effect blocked: " + suggestion.nodeId());
        }
        if (!builder.hasType(suggestion.nodeId())
                && !builder.addNode(suggestion.nodeId(), builder.depth(), 40f)) {
            return WireResult.fail("Exceeded maxNodes inserting converter.");
        }
        String convRef = builder.refForType(suggestion.nodeId());
        String convIn = findCompatibleInput(suggestion.nodeId(), outType);
        String convOut = findCompatibleOutput(suggestion.nodeId(), inType);
        if (convIn == null || convOut == null) {
            return WireResult.fail("Converter ports unresolved for " + suggestion.nodeId());
        }
        builder.connect(sourceRef, sourcePortId, convRef, convIn);
        builder.connect(convRef, convOut, targetRef, targetPortId);
        reasons.add("Inserted converter " + suggestion.nodeId() + " for " + outType + " → " + inType);
        return WireResult.ok(AiComposeCostPolicy.COST_CONVERSION + AiComposeCostPolicy.COST_NEW_NODE);
    }

    // --- Finalize ---

    private static AiComposeResult finalizePlan(
            PlanBuilder builder,
            AiComposeRequest request,
            List<String> reasons,
            double cost,
            NodeSemanticCatalog catalog
    ) {
        AiGraphPlan plan = builder.toPlan(String.join(" ", reasons));
        AiGraphDslSupport.PlanValidationResult validation =
                AiGraphDslSupport.validatePlan(plan, NodeRegistry.getInstance());
        // Composer success ⟺ full validatePlan (no Required-input soft filter).
        if (validation.errors() != null && !validation.errors().isEmpty() && registryHasAny(builder)) {
            return AiComposeResult.abstain(
                    AiComposeResult.ABSTAIN_CODE,
                    "Plan validation failed: " + validation.errors(),
                    Set.of(),
                    Set.of()
            );
        }

        AiPlanCapabilityCoverage.CoverageResult coverage =
                AiPlanCapabilityCoverage.analyze(request.prompt(), plan);
        Set<NodeCapability> present = new HashSet<>(coverage.present());
        for (AiPlanNode node : plan.nodes()) {
            present.addAll(catalog.capabilities(node.typeId()));
        }

        Set<NodeCapability> missing = EnumSet.noneOf(NodeCapability.class);
        for (NodeCapability cap : coverage.missing()) {
            if (cap == NodeCapability.APPLY || cap == NodeCapability.WORLD_APPLY) {
                if (request.goal() != AiComposeGoal.WORLD_OUTPUT) {
                    continue;
                }
                if (present.contains(NodeCapability.APPLY) || present.contains(NodeCapability.WORLD_APPLY)
                        || builder.hasType("output.execute.apply_changes")) {
                    continue;
                }
            }
            if (seedCovers(cap, builder) || present.contains(cap)) {
                continue;
            }
            // Soft markers that seeds imply.
            if (cap == NodeCapability.SPHERE || cap == NodeCapability.BOX || cap == NodeCapability.CURVE
                    || cap == NodeCapability.ROOF || cap == NodeCapability.TERRAIN
                    || cap == NodeCapability.SDF || cap == NodeCapability.FIELD
                    || cap == NodeCapability.MATERIAL || cap == NodeCapability.ARRAY) {
                continue;
            }
            missing.add(cap);
        }
        // PREVIEW must be present for PREVIEW/WORLD goals.
        if (request.goal() != AiComposeGoal.CAPABILITY_SET
                && !present.contains(NodeCapability.PREVIEW)
                && builder.typeIds().stream().noneMatch(AiSemanticComposer::isPreviewTerminal)) {
            missing.add(NodeCapability.PREVIEW);
        }

        if (!missing.isEmpty()) {
            return AiComposeResult.abstain(
                    AiComposeResult.ABSTAIN_CODE,
                    "Missing capabilities: " + missing,
                    present,
                    missing
            );
        }

        AiPlanValidator.GateResult applyGate = new AiPlanValidator().checkBeforeApply(plan);
        if (!applyGate.allowed()) {
            return AiComposeResult.abstain(
                    AiComposeResult.ABSTAIN_CODE,
                    "Apply gate rejected: " + applyGate.rejectionMessage(),
                    present,
                    Set.of()
            );
        }
        return AiComposeResult.success(plan, present, reasons, cost);
    }

    private static boolean seedCovers(NodeCapability cap, PlanBuilder builder) {
        return switch (cap) {
            case SPHERE -> builder.hasType("geometry.primitives.sphere");
            case BOX -> builder.hasType("geometry.primitives.box");
            case WALL -> builder.hasType("geometry.architectural_primitives.wall_slab");
            case WINDOW, OPENING, ARRAY ->
                    builder.hasType("geometry.architectural_primitives.window_array");
            case SWEEP -> builder.hasType("geometry.solids.sweep");
            case EXTRUDE -> builder.hasType("geometry.solids.extrude_region")
                    || builder.hasType("geometry.solids.extrude");
            case BOOLEAN_CUT -> builder.hasType("geometry.boolean.difference");
            case CURVE -> builder.typeIds().stream().anyMatch(t -> t.contains("curves."));
            default -> false;
        };
    }

    private static boolean registryHasAny(PlanBuilder builder) {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (registry == null || registry.getNodeCount() == 0) {
            return false;
        }
        for (String typeId : builder.typeIds()) {
            try {
                if (registry.getNodeInfo(typeId) != null) {
                    return true;
                }
            } catch (Exception ignored) {
                // continue
            }
        }
        return false;
    }

    // --- Effect / edge policy ---

    static boolean isEffectAllowed(NodeEffect effect, boolean allowWorldWrite) {
        if (effect == null || effect == NodeEffect.UNSPECIFIED || effect == NodeEffect.PURE
                || effect == NodeEffect.CONTEXT_READ || effect == NodeEffect.WORLD_READ
                || effect == NodeEffect.PREVIEW_WRITE || effect == NodeEffect.COMPOSITE
                || effect == NodeEffect.EDITOR_ONLY) {
            return true;
        }
        if (effect == NodeEffect.WORLD_WRITE) {
            return allowWorldWrite;
        }
        // CONTEXT_WRITE / FILE_IO / NETWORK / UI_EFFECT blocked
        return false;
    }

    private static boolean isEdgeAllowed(
            NodeSemanticEdge edge,
            String sourceTypeId,
            NodeSemanticCatalog catalog,
            boolean allowWorldWrite
    ) {
        if (edge.kind() == null) {
            return false;
        }
        if (edge.sourcePortId() != null && edge.sourcePortId().contains(":")) {
            return false; // no orientation expansion without caller key
        }
        if (edge.sourcePortId() != null && edge.kind() != NodeSemanticEdgeKind.EXACT) {
            String p = edge.sourcePortId().toLowerCase(Locale.ROOT);
            if (p.contains("height") || p.contains("radius") || p.contains("count")
                    || p.contains("spacing") || p.contains("width") || p.contains("depth")) {
                return false;
            }
            NodeDataType sourceType = portDataType(sourceTypeId, edge.sourcePortId(), true);
            if (sourceType != null && SILENT_SCALARS.contains(sourceType)) {
                return false;
            }
        }
        return isEffectAllowed(catalog.effect(edge.targetNodeId()), allowWorldWrite);
    }

    /** Package-visible for scalar-noise contract tests. */
    static boolean wouldExpandScalarEdge(NodeSemanticEdgeKind kind, NodeDataType sourceType) {
        if (sourceType != null && SILENT_SCALARS.contains(sourceType)) {
            return kind == NodeSemanticEdgeKind.EXACT;
        }
        return true;
    }

    private static boolean isPreviewTerminal(String typeId) {
        return typeId != null && typeId.startsWith("output.preview.");
    }

    private static boolean isApplyTerminal(String typeId) {
        return "output.execute.apply_changes".equalsIgnoreCase(typeId);
    }

    private static boolean isUnsupportedDomainHeavy(Set<NodeDomain> domains) {
        if (domains == null || domains.isEmpty()) {
            return false;
        }
        boolean heavy = domains.contains(NodeDomain.SDF)
                || domains.contains(NodeDomain.FIELD)
                || domains.contains(NodeDomain.TERRAIN);
        boolean supported = domains.stream().anyMatch(SUPPORTED_DOMAINS::contains);
        return heavy && !supported;
    }

    private static boolean hasSupportedCapability(Set<NodeCapability> caps) {
        if (caps == null) {
            return false;
        }
        return caps.contains(NodeCapability.SPHERE)
                || caps.contains(NodeCapability.BOX)
                || caps.contains(NodeCapability.WALL)
                || caps.contains(NodeCapability.WINDOW)
                || caps.contains(NodeCapability.SWEEP)
                || caps.contains(NodeCapability.EXTRUDE)
                || caps.contains(NodeCapability.PREVIEW)
                || caps.contains(NodeCapability.CURVE);
    }

    private static String visitKey(String typeId, String continuePort, String sourcePort) {
        return Objects.toString(typeId, "")
                + "|" + Objects.toString(continuePort, "")
                + "|" + Objects.toString(sourcePort, "");
    }

    /**
     * Package-visible test helper: force a conversion wire between two node types.
     * Returns the resulting plan, or {@code null} with failure reason in {@code failOut[0]}.
     */
    static AiGraphPlan wireConversionFixture(
            String sourceTypeId,
            String sourcePortId,
            String targetTypeId,
            String targetPortId,
            String[] failOut
    ) {
        PlanBuilder builder = new PlanBuilder(8);
        if (!builder.addNode(sourceTypeId, 0, 0) || !builder.addNode(targetTypeId, 1, 0)) {
            if (failOut != null && failOut.length > 0) {
                failOut[0] = "Failed to place fixture nodes";
            }
            return null;
        }
        List<String> reasons = new ArrayList<>();
        AiComposeRequest request = new AiComposeRequest(
                "conversion fixture",
                Set.of(),
                EnumSet.noneOf(NodeDomain.class),
                List.of(),
                AiComposeGoal.PREVIEW,
                8
        );
        WireResult wire = wireWithConversion(
                builder,
                builder.refForType(sourceTypeId),
                sourcePortId,
                builder.refForType(targetTypeId),
                targetPortId,
                NodeSemanticCatalog.get(),
                request,
                reasons
        );
        if (wire.failed()) {
            if (failOut != null && failOut.length > 0) {
                failOut[0] = wire.message();
            }
            return null;
        }
        return builder.toPlan(String.join(" ", reasons));
    }

    private static boolean containsAny(String text, String... keys) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String key : keys) {
            if (key != null && lower.contains(key.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    // --- Port helpers (registry introspection only) ---

    private static final List<String> PREFERRED_OUTPUTS = List.of(
            "output_geometry",
            "output_profile",
            "output_path",
            "output_surface_strip",
            "output_openings",
            "output_blocks",
            "output_placements",
            "output_region"
    );

    private static String primaryOutputPort(String typeId) {
        INode node = tryCreate(typeId);
        if (node == null || node.getOutputPorts() == null) {
            return guessOutput(typeId);
        }
        Map<String, IPort> byId = new HashMap<>();
        for (IPort port : node.getOutputPorts()) {
            if (port != null && port.getId() != null) {
                byId.put(port.getId(), port);
            }
        }
        for (String preferred : PREFERRED_OUTPUTS) {
            IPort port = byId.get(preferred);
            if (port != null && port.getDataType() != NodeDataType.EXEC) {
                return port.getId();
            }
        }
        for (IPort port : node.getOutputPorts()) {
            if (port != null && port.getDataType() != NodeDataType.EXEC
                    && port.getDataType() != NodeDataType.BOOLEAN
                    && port.getDataType() != NodeDataType.STRING) {
                return port.getId();
            }
        }
        return guessOutput(typeId);
    }

    private static String findOutputPort(String typeId, String preferredId, NodeDataType type) {
        INode node = tryCreate(typeId);
        if (node != null && node.getOutputPorts() != null) {
            if (preferredId != null) {
                for (IPort port : node.getOutputPorts()) {
                    if (preferredId.equals(port.getId())) {
                        return port.getId();
                    }
                }
            }
            if (type != null) {
                for (IPort port : node.getOutputPorts()) {
                    if (port.getDataType() == type) {
                        return port.getId();
                    }
                }
            }
        }
        return preferredId != null ? preferredId : primaryOutputPort(typeId);
    }

    private static String findInputPort(String typeId, String preferredId, NodeDataType type) {
        INode node = tryCreate(typeId);
        if (node != null && node.getInputPorts() != null) {
            if (preferredId != null) {
                for (IPort port : node.getInputPorts()) {
                    if (preferredId.equals(port.getId())) {
                        return port.getId();
                    }
                }
            }
            if (type != null) {
                for (IPort port : node.getInputPorts()) {
                    if (port.getDataType() == type) {
                        return port.getId();
                    }
                }
            }
        }
        return preferredId;
    }

    private static String findStructuralInput(String typeId, NodeDataType prefer) {
        INode node = tryCreate(typeId);
        if (node == null || node.getInputPorts() == null) {
            return null;
        }
        if (prefer != null) {
            for (IPort port : node.getInputPorts()) {
                if (port.getDataType() == prefer) {
                    return port.getId();
                }
            }
        }
        for (IPort port : node.getInputPorts()) {
            if (port != null && port.getDataType() != NodeDataType.EXEC
                    && !SILENT_SCALARS.contains(port.getDataType())) {
                return port.getId();
            }
        }
        return null;
    }

    private static String findCompatibleInput(String typeId, NodeDataType want) {
        INode node = tryCreate(typeId);
        if (node == null || node.getInputPorts() == null) {
            return null;
        }
        for (IPort port : node.getInputPorts()) {
            if (port.getDataType() == want
                    || TypeConversionRegistry.isImplicitlyConnectable(want, port.getDataType())) {
                return port.getId();
            }
        }
        return findStructuralInput(typeId, null);
    }

    private static String findCompatibleOutput(String typeId, NodeDataType want) {
        INode node = tryCreate(typeId);
        if (node == null || node.getOutputPorts() == null) {
            return null;
        }
        for (IPort port : node.getOutputPorts()) {
            if (port.getDataType() == want
                    || TypeConversionRegistry.isImplicitlyConnectable(port.getDataType(), want)) {
                return port.getId();
            }
        }
        return primaryOutputPort(typeId);
    }

    private static NodeDataType portDataType(String typeId, String portId, boolean output) {
        if (typeId == null || portId == null) {
            return null;
        }
        INode node = tryCreate(typeId);
        if (node == null) {
            return null;
        }
        List<IPort> ports = output ? node.getOutputPorts() : node.getInputPorts();
        if (ports == null) {
            return null;
        }
        for (IPort port : ports) {
            if (portId.equals(port.getId())) {
                return port.getDataType();
            }
        }
        return null;
    }

    private static INode tryCreate(String typeId) {
        try {
            return NodeRegistry.getInstance().createNodeInstance(typeId);
        } catch (Exception e) {
            return null;
        }
    }

    private static String guessOutput(String typeId) {
        if (typeId == null) {
            return "output";
        }
        if (typeId.contains("voxelize") || typeId.contains("surface_strip_to_blocks")) {
            return "output_blocks";
        }
        if (typeId.contains("assign_block")) {
            return "output_placements";
        }
        if (typeId.contains("window_array")) {
            return "output_openings";
        }
        if (typeId.contains("profile") && !typeId.contains("to_region")) {
            return "output_profile";
        }
        if (typeId.contains("helix") || typeId.contains("curves.")) {
            return "output_path";
        }
        return "output_geometry";
    }

    // --- Builder ---

    private static final class PlanBuilder {
        private final int maxNodes;
        private final Map<String, String> typeToRef = new HashMap<>();
        private final Map<String, String> refToType = new HashMap<>();
        private final Map<String, Integer> shortNameSeq = new HashMap<>();
        private final List<AiPlanNode> nodes = new ArrayList<>();
        private final List<AiPlanConnection> connections = new ArrayList<>();
        private final Set<String> connectedInputs = new HashSet<>();
        private final Set<String> connectionKeys = new HashSet<>();
        private int maxDepth;

        PlanBuilder(int maxNodes) {
            this.maxNodes = maxNodes;
        }

        int depth() {
            return maxDepth;
        }

        int nodeCount() {
            return nodes.size();
        }

        boolean addNode(String typeId, int depth, float y) {
            String key = typeId.toLowerCase(Locale.ROOT);
            if (typeToRef.containsKey(key)) {
                return true;
            }
            if (nodes.size() >= maxNodes) {
                return false;
            }
            String ref = nextRef(typeId);
            typeToRef.put(key, ref);
            refToType.put(ref, typeId);
            nodes.add(new AiPlanNode(ref, typeId, depth * 280.0f, y, null));
            maxDepth = Math.max(maxDepth, depth);
            return true;
        }

        private String nextRef(String typeId) {
            String shortName = typeId;
            int dot = typeId.lastIndexOf('.');
            if (dot >= 0 && dot < typeId.length() - 1) {
                shortName = typeId.substring(dot + 1);
            }
            shortName = shortName.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase(Locale.ROOT);
            int seq = shortNameSeq.merge(shortName, 1, Integer::sum);
            return shortName + "_" + seq;
        }

        String refForType(String typeId) {
            return typeToRef.get(typeId.toLowerCase(Locale.ROOT));
        }

        String typeForRef(String ref) {
            return refToType.get(ref);
        }

        boolean hasType(String typeId) {
            return typeToRef.containsKey(typeId.toLowerCase(Locale.ROOT));
        }

        Set<String> typeIds() {
            return Set.copyOf(refToType.values());
        }

        void connect(String fromRef, String fromPort, String toRef, String toPort) {
            if (fromRef == null || toRef == null || fromPort == null || toPort == null) {
                return;
            }
            String key = fromRef + "|" + fromPort + "|" + toRef + "|" + toPort;
            if (!connectionKeys.add(key)) {
                return;
            }
            connections.add(new AiPlanConnection(fromRef, fromPort, toRef, toPort));
            connectedInputs.add(toRef + "." + toPort);
        }

        boolean hasConnection(String fromRef, String fromPort, String toRef, String toPort) {
            return connectionKeys.contains(fromRef + "|" + fromPort + "|" + toRef + "|" + toPort);
        }

        boolean isConnected(String toRef, String toPort) {
            return connectedInputs.contains(toRef + "." + toPort);
        }

        AiGraphPlan toPlan(String summary) {
            return new AiGraphPlan(summary, List.copyOf(nodes), List.copyOf(connections), List.of());
        }
    }

    private record ScoredSeed(String typeId, int score) {
    }

    private record ProducerMatch(String ref, String typeId, String portId) {
    }

    private record PathResult(List<AiComposeSearchState.Step> steps, List<String> reasons, double cost) {
        static PathResult fromState(AiComposeSearchState state) {
            List<String> reasons = new ArrayList<>();
            for (AiComposeSearchState.Step step : state.steps()) {
                if (step.reason() != null && !step.reason().isBlank()) {
                    reasons.add(step.reason());
                } else {
                    reasons.add("Connect " + step.fromTypeId() + " → " + step.toTypeId());
                }
            }
            return new PathResult(state.steps(), reasons, state.cost());
        }
    }

    private record ApplyResult(String failMessage, double extraCost) {
        static ApplyResult ok(double extra) {
            return new ApplyResult(null, extra);
        }

        static ApplyResult fail(String message) {
            return new ApplyResult(message, 0);
        }
    }

    private record WireResult(String message, double extraCost) {
        static WireResult ok(double extra) {
            return new WireResult(null, extra);
        }

        static WireResult fail(String message) {
            return new WireResult(message, 0);
        }

        boolean failed() {
            return message != null;
        }
    }

}
