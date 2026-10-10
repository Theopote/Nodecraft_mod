package com.nodecraft.gui.ai.compose;

import com.nodecraft.gui.ai.AiGraphDslSupport;
import com.nodecraft.gui.ai.AiPlanCapabilityCoverage;
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
 * Deterministic Semantic Composer v1 — completes small Preview-first workflows from
 * {@link NodeSemanticCatalog} edges. Abstain rather than invent insecure graphs.
 */
public final class AiSemanticComposer {

    private static final String SPHERE = "geometry.primitives.sphere";
    private static final String BOX = "geometry.primitives.box";
    private static final String WALL = "geometry.architectural_primitives.wall_slab";
    private static final String WINDOW_ARRAY = "geometry.architectural_primitives.window_array";
    private static final String DIFFERENCE = "geometry.boolean.difference";
    private static final String VOXELIZE = "geometry.voxel.voxelize_geometry";
    private static final String SURFACE_STRIP_TO_BLOCKS = "geometry.voxel.surface_strip_to_blocks";
    private static final String ASSIGN = "material.basic_assignment.assign_block_type";
    private static final String BLOCK_TYPE = "input.type_selectors.block_type_selector";
    private static final String PREVIEW_BLOCKS = "output.preview.preview_blocks";
    private static final String APPLY = "output.execute.apply_changes";
    private static final String SWEEP = "geometry.solids.sweep";
    private static final String RECT_PROFILE = "geometry.profiles.rectangle_profile";
    private static final String HELIX_PATH = "geometry.curves.helix";
    private static final String PROFILE_TO_REGION = "geometry.profiles.profile_to_region";
    private static final String EXTRUDE_REGION = "geometry.solids.extrude_region";

    /** Scalar ports must not expand via CATEGORY/TYPE flood — EXACT edges only. */
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

        AiComposeResult architecture = tryComposeArchitecture(request, seeds, builder, reasons, catalog);
        if (architecture != null) {
            return architecture;
        }

        AiComposeResult sweep = tryComposeSweep(request, seeds, builder, reasons, catalog);
        if (sweep != null) {
            return sweep;
        }

        AiComposeResult extrude = tryComposeExtrude(request, seeds, builder, reasons, catalog);
        if (extrude != null) {
            return extrude;
        }

        // Single-seed (or primary seed) downstream completion toward preview.
        String primary = seeds.getFirst();
        if (!builder.addSeed(primary, 0, 0)) {
            return AiComposeResult.abstain("Exceeded maxNodes while placing seed.");
        }
        reasons.add("Seeded " + primary);

        String terminalRef = builder.refForType(primary);
        String outPort = primaryOutputPort(primary);
        // Prefer the canonical material→Preview chain for geometry producers; UCS is
        // conservative (EXACT/CATEGORY only) and may miss the bake path or invent noise.
        PathResult path = null;
        if (hasGeometryPrimaryOutput(primary)) {
            path = materialPreviewPath(primary);
        } else {
            path = searchDownstreamToPreview(primary, outPort, request, catalog);
            if (path == null) {
                path = materialPreviewPath(primary);
            }
        }
        if (path == null) {
            return AiComposeResult.abstain("No semantic path from seed to Preview.");
        }
        cost += path.cost();
        reasons.addAll(path.reasons());

        ApplyPathResult applied = applyPath(builder, terminalRef, outPort, path, catalog, request);
        if (applied.abstainMessage() != null) {
            return AiComposeResult.abstain(applied.abstainMessage());
        }
        terminalRef = applied.terminalRef();
        String terminalOut = applied.terminalOutPort();

        if (request.goal() == AiComposeGoal.WORLD_OUTPUT && request.allowWorldWrite()) {
            if (!appendApply(builder, terminalRef, terminalOut, reasons)) {
                return AiComposeResult.abstain("Could not append Apply Changes within node budget.");
            }
            cost += AiComposeCostPolicy.COST_NEW_NODE;
        }

        return finalizePlan(builder, request, reasons, cost, catalog);
    }

    private static AiComposeResult tryComposeArchitecture(
            AiComposeRequest request,
            List<String> seeds,
            PlanBuilder builder,
            List<String> reasons,
            NodeSemanticCatalog catalog
    ) {
        Set<NodeCapability> required = request.requiredCapabilities();
        boolean wantsWall = required.contains(NodeCapability.WALL) || seeds.contains(WALL);
        boolean wantsWindow = required.contains(NodeCapability.WINDOW)
                || required.contains(NodeCapability.OPENING)
                || seeds.contains(WINDOW_ARRAY);
        if (!(wantsWall && wantsWindow)) {
            return null;
        }

        if (!builder.addSeed(WALL, 0, 0) || !builder.addSeed(WINDOW_ARRAY, 0, 180)) {
            return AiComposeResult.abstain("Exceeded maxNodes placing Wall/Window seeds.");
        }
        reasons.add("Seeded Wall Slab and Window Array");

        if (!builder.addNode(DIFFERENCE, 1, 90)) {
            return AiComposeResult.abstain("Exceeded maxNodes placing Difference.");
        }
        reasons.add("Added Difference for BOOLEAN_CUT (window openings cutter)");

        String wallRef = builder.refForType(WALL);
        String windowRef = builder.refForType(WINDOW_ARRAY);
        String diffRef = builder.refForType(DIFFERENCE);

        String wallGeom = findOutputPort(WALL, "output_geometry", NodeDataType.GEOMETRY);
        String openings = findOutputPort(WINDOW_ARRAY, "output_openings", null);
        String baseIn = findInputPort(DIFFERENCE, "input_base", null);
        String cutterIn = findInputPort(DIFFERENCE, "input_cutter", null);
        if (wallGeom == null || openings == null || baseIn == null || cutterIn == null) {
            return AiComposeResult.abstain("Architecture ports unresolved for Wall/Window/Difference.");
        }

        WireResult wallWire = wireWithConversion(builder, wallRef, wallGeom, diffRef, baseIn, catalog, request);
        if (wallWire.failed()) {
            return AiComposeResult.abstain(wallWire.message());
        }
        WireResult cutWire = wireWithConversion(builder, windowRef, openings, diffRef, cutterIn, catalog, request);
        if (cutWire.failed()) {
            return AiComposeResult.abstain(cutWire.message());
        }

        String diffOut = primaryOutputPort(DIFFERENCE);
        PathResult previewPath = materialPreviewPath(DIFFERENCE);
        ApplyPathResult applied = applyPath(builder, diffRef, diffOut, previewPath, catalog, request);
        if (applied.abstainMessage() != null) {
            return AiComposeResult.abstain(applied.abstainMessage());
        }
        reasons.addAll(previewPath.reasons());
        if (request.goal() == AiComposeGoal.WORLD_OUTPUT && request.allowWorldWrite()) {
            if (!appendApply(builder, applied.terminalRef(), applied.terminalOutPort(), reasons)) {
                return AiComposeResult.abstain("Could not append Apply Changes within node budget.");
            }
        }
        return finalizePlan(builder, request, reasons, 8.0, catalog);
    }

    private static AiComposeResult tryComposeSweep(
            AiComposeRequest request,
            List<String> seeds,
            PlanBuilder builder,
            List<String> reasons,
            NodeSemanticCatalog catalog
    ) {
        boolean wantsSweep = request.requiredCapabilities().contains(NodeCapability.SWEEP)
                || seeds.contains(SWEEP)
                || seeds.contains(HELIX_PATH)
                || containsAny(request.prompt(), "sweep", "扫掠", "放样", "helix", "螺旋", "arch", "拱");
        if (!wantsSweep) {
            return null;
        }

        if (!builder.addSeed(HELIX_PATH, 0, 0)
                || !builder.addSeed(RECT_PROFILE, 0, 180)
                || !builder.addNode(SWEEP, 1, 90)) {
            return AiComposeResult.abstain("Exceeded maxNodes placing Sweep seeds.");
        }
        reasons.add("Seeded path + profile → Sweep");

        String pathRef = builder.refForType(HELIX_PATH);
        String profileRef = builder.refForType(RECT_PROFILE);
        String sweepRef = builder.refForType(SWEEP);
        String pathOut = findOutputPort(HELIX_PATH, "output_path", NodeDataType.PATH);
        String profileOut = findOutputPort(RECT_PROFILE, "output_profile", NodeDataType.POLYGON_PROFILE);
        String pathIn = findInputPort(SWEEP, "input_path", NodeDataType.PATH);
        String profileIn = findInputPort(SWEEP, "input_profile", NodeDataType.POLYGON_PROFILE);
        if (pathOut == null || profileOut == null || pathIn == null || profileIn == null) {
            return AiComposeResult.abstain("Sweep ports unresolved.");
        }
        if (wireWithConversion(builder, pathRef, pathOut, sweepRef, pathIn, catalog, request).failed()
                || wireWithConversion(builder, profileRef, profileOut, sweepRef, profileIn, catalog, request).failed()) {
            return AiComposeResult.abstain("Failed to wire Sweep inputs.");
        }

        // Prefer surface-strip bake when present as EXACT/CATEGORY neighbor, else voxelize geometry.
        String stripOut = findOutputPort(SWEEP, "output_surface_strip", NodeDataType.SURFACE_STRIP);
        String geomOut = primaryOutputPort(SWEEP);
        PathResult path;
        String fromPort;
        if (stripOut != null && hasExactOrCategoryEdge(catalog, SWEEP, stripOut, SURFACE_STRIP_TO_BLOCKS)) {
            if (!builder.addNode(SURFACE_STRIP_TO_BLOCKS, 2, 90)) {
                return AiComposeResult.abstain("Exceeded maxNodes for SurfaceStripToBlocks.");
            }
            reasons.add("Added SurfaceStripToBlocks (PURE voxel conversion)");
            String stripRef = builder.refForType(SURFACE_STRIP_TO_BLOCKS);
            String stripIn = findInputPort(SURFACE_STRIP_TO_BLOCKS, null, NodeDataType.SURFACE_STRIP);
            if (stripIn == null
                    || wireWithConversion(builder, sweepRef, stripOut, stripRef, stripIn, catalog, request).failed()) {
                return AiComposeResult.abstain("Failed to wire SurfaceStripToBlocks.");
            }
            fromPort = primaryOutputPort(SURFACE_STRIP_TO_BLOCKS);
            path = blocksMaterialPreviewPath(SURFACE_STRIP_TO_BLOCKS);
            ApplyPathResult applied = applyPath(builder, stripRef, fromPort, path, catalog, request);
            if (applied.abstainMessage() != null) {
                return AiComposeResult.abstain(applied.abstainMessage());
            }
            reasons.addAll(path.reasons());
            maybeApply(builder, request, applied, reasons);
            return finalizePlan(builder, request, reasons, 12.0, catalog);
        }

        fromPort = geomOut;
        path = materialPreviewPath(SWEEP);
        ApplyPathResult applied = applyPath(builder, sweepRef, fromPort, path, catalog, request);
        if (applied.abstainMessage() != null) {
            return AiComposeResult.abstain(applied.abstainMessage());
        }
        reasons.addAll(path.reasons());
        maybeApply(builder, request, applied, reasons);
        return finalizePlan(builder, request, reasons, 12.0, catalog);
    }

    private static AiComposeResult tryComposeExtrude(
            AiComposeRequest request,
            List<String> seeds,
            PlanBuilder builder,
            List<String> reasons,
            NodeSemanticCatalog catalog
    ) {
        boolean wantsExtrude = request.requiredCapabilities().contains(NodeCapability.EXTRUDE)
                || containsAny(request.prompt(), "extrude", "拉伸", "profile", "轮廓");
        if (!wantsExtrude || request.requiredCapabilities().contains(NodeCapability.WALL)) {
            return null;
        }
        if (!builder.addSeed(RECT_PROFILE, 0, 0)
                || !builder.addNode(PROFILE_TO_REGION, 1, 0)
                || !builder.addNode(EXTRUDE_REGION, 2, 0)) {
            return AiComposeResult.abstain("Exceeded maxNodes for Extrude chain.");
        }
        reasons.add("Seeded Profile → Region → Extrude");
        String profileRef = builder.refForType(RECT_PROFILE);
        String regionRef = builder.refForType(PROFILE_TO_REGION);
        String extrudeRef = builder.refForType(EXTRUDE_REGION);
        if (wireWithConversion(builder, profileRef, primaryOutputPort(RECT_PROFILE),
                regionRef, findInputPort(PROFILE_TO_REGION, null, null), catalog, request).failed()
                || wireWithConversion(builder, regionRef, primaryOutputPort(PROFILE_TO_REGION),
                extrudeRef, findInputPort(EXTRUDE_REGION, null, null), catalog, request).failed()) {
            return AiComposeResult.abstain("Failed to wire Extrude chain.");
        }
        PathResult path = materialPreviewPath(EXTRUDE_REGION);
        ApplyPathResult applied = applyPath(
                builder, extrudeRef, primaryOutputPort(EXTRUDE_REGION), path, catalog, request);
        if (applied.abstainMessage() != null) {
            return AiComposeResult.abstain(applied.abstainMessage());
        }
        reasons.addAll(path.reasons());
        maybeApply(builder, request, applied, reasons);
        return finalizePlan(builder, request, reasons, 10.0, catalog);
    }

    private static void maybeApply(
            PlanBuilder builder,
            AiComposeRequest request,
            ApplyPathResult applied,
            List<String> reasons
    ) {
        if (request.goal() == AiComposeGoal.WORLD_OUTPUT && request.allowWorldWrite()) {
            appendApply(builder, applied.terminalRef(), applied.terminalOutPort(), reasons);
        }
    }

    private static boolean hasExactOrCategoryEdge(
            NodeSemanticCatalog catalog,
            String fromType,
            String fromPort,
            String toType
    ) {
        for (NodeSemanticEdge edge : catalog.effectiveDownstream(fromType, fromPort, null)) {
            if (toType.equalsIgnoreCase(edge.targetNodeId())
                    && (edge.kind() == NodeSemanticEdgeKind.EXACT || edge.kind() == NodeSemanticEdgeKind.CATEGORY)) {
                return true;
            }
        }
        // Also accept known PURE conversion even if rules omit it.
        return SURFACE_STRIP_TO_BLOCKS.equals(toType);
    }

    private static PathResult searchDownstreamToPreview(
            String seedTypeId,
            String seedPort,
            AiComposeRequest request,
            NodeSemanticCatalog catalog
    ) {
        PriorityQueue<AiComposeSearchState> queue = new PriorityQueue<>();
        Set<NodeCapability> seedCaps = new HashSet<>(catalog.capabilities(seedTypeId));
        LinkedHashSet<String> startPath = new LinkedHashSet<>();
        startPath.add(seedTypeId);
        queue.add(new AiComposeSearchState(startPath, seedTypeId, seedPort, seedCaps, 0, List.of()));

        Set<String> visited = new HashSet<>();
        visited.add(visitKey(seedTypeId, seedPort));

        int expansions = 0;
        while (!queue.isEmpty() && expansions < 200) {
            expansions++;
            AiComposeSearchState state = queue.poll();
            if (state.typePath().size() > request.maxNodes()) {
                continue;
            }
            if (isPreviewTerminal(state.frontierTypeId())) {
                return PathResult.fromState(state);
            }

            List<NodeSemanticEdge> edges = catalog.effectiveDownstream(
                    state.frontierTypeId(), state.frontierPortKey(), null);
            // Physical-port fallback when frontier port is unknown.
            if (edges.isEmpty()) {
                edges = catalog.effectiveDownstream(state.frontierTypeId());
            }

            for (NodeSemanticEdge edge : edges) {
                if (edge == null || edge.targetNodeId() == null) {
                    continue;
                }
                // v1: no TYPE flood — EXACT/CATEGORY only.
                if (edge.kind() != NodeSemanticEdgeKind.EXACT
                        && edge.kind() != NodeSemanticEdgeKind.CATEGORY) {
                    continue;
                }
                if (!isEdgeAllowed(edge, state.frontierTypeId(), catalog, request.allowWorldWrite())) {
                    continue;
                }
                String nextType = edge.targetNodeId();
                String nextInPort = edge.targetPortId();
                String key = visitKey(nextType, edge.sourcePortId());
                if (visited.contains(key) || state.typePath().contains(nextType)) {
                    continue;
                }
                if (!isEffectAllowed(catalog.effect(nextType), request.allowWorldWrite())) {
                    continue;
                }

                Set<NodeCapability> nextCovered = EnumSet.noneOf(NodeCapability.class);
                nextCovered.addAll(state.covered());
                nextCovered.addAll(catalog.capabilities(nextType));

                double stepCost = AiComposeCostPolicy.edgeCost(edge.kind())
                        + AiComposeCostPolicy.nodePenalty(catalog.effect(nextType));
                // Continue search from the new node's primary output, not its input port.
                String continuePort = primaryOutputPort(nextType);
                AiComposeSearchState.Step step = new AiComposeSearchState.Step(
                        state.frontierTypeId(),
                        edge.sourcePortId() != null ? edge.sourcePortId() : state.frontierPortKey(),
                        nextType,
                        nextInPort,
                        edge.reason(),
                        false
                );
                LinkedHashSet<String> nextPath = new LinkedHashSet<>(state.typePath());
                nextPath.add(nextType);
                List<AiComposeSearchState.Step> nextSteps = new ArrayList<>(state.steps());
                nextSteps.add(step);
                AiComposeSearchState next = new AiComposeSearchState(
                        nextPath, nextType, continuePort, nextCovered, state.cost() + stepCost, nextSteps);
                visited.add(key);
                queue.add(next);
            }
        }
        return null;
    }

    private static PathResult materialPreviewPath(String fromTypeId) {
        List<AiComposeSearchState.Step> steps = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        steps.add(new AiComposeSearchState.Step(fromTypeId, null, VOXELIZE, null,
                "Voxelize geometry for block preview", false));
        reasons.add("Added Voxelize because block preview needs coordinates");
        steps.add(new AiComposeSearchState.Step(VOXELIZE, null, ASSIGN, null,
                "Assign block type for placements", false));
        reasons.add("Added Assign Block Type because Preview Blocks needs typed placements");
        steps.add(new AiComposeSearchState.Step(ASSIGN, null, PREVIEW_BLOCKS, null,
                "Preview Blocks terminal", false));
        reasons.add("Added Preview Blocks as the default preview terminal");
        return new PathResult(steps, reasons, steps.size() * 2.0);
    }

    private static PathResult blocksMaterialPreviewPath(String fromTypeId) {
        List<AiComposeSearchState.Step> steps = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        steps.add(new AiComposeSearchState.Step(fromTypeId, null, ASSIGN, null,
                "Assign block type for placements", false));
        reasons.add("Added Assign Block Type because Preview Blocks needs typed placements");
        steps.add(new AiComposeSearchState.Step(ASSIGN, null, PREVIEW_BLOCKS, null,
                "Preview Blocks terminal", false));
        reasons.add("Added Preview Blocks as the default preview terminal");
        return new PathResult(steps, reasons, steps.size() * 2.0);
    }

    private static ApplyPathResult applyPath(
            PlanBuilder builder,
            String fromRef,
            String fromPort,
            PathResult path,
            NodeSemanticCatalog catalog,
            AiComposeRequest request
    ) {
        String currentRef = fromRef;
        String currentPort = fromPort;
        if (path == null) {
            return ApplyPathResult.fail("Empty path.");
        }
        for (AiComposeSearchState.Step step : path.steps()) {
            String toType = step.toTypeId();
            if (!builder.addNode(toType, builder.depth() + 1, 0)) {
                return ApplyPathResult.fail("Exceeded maxNodes applying path to " + toType);
            }
            if (!isEffectAllowed(catalog.effect(toType), request.allowWorldWrite())) {
                return ApplyPathResult.fail("Blocked effect for " + toType);
            }
            String toRef = builder.refForType(toType);
            String outPort = step.fromPortId() != null ? step.fromPortId() : currentPort;
            if (outPort == null) {
                outPort = primaryOutputPort(builder.typeForRef(currentRef));
            }
            String inPort = step.toPortId();
            if (inPort == null) {
                inPort = findInputPort(toType, null, null);
            }
            if (ASSIGN.equals(toType)) {
                // Ensure block type selector exists.
                if (builder.refForType(BLOCK_TYPE) == null && !builder.addNode(BLOCK_TYPE, builder.depth(), -120)) {
                    return ApplyPathResult.fail("Exceeded maxNodes for block type selector.");
                }
                String blockRef = builder.refForType(BLOCK_TYPE);
                String blockOut = primaryOutputPort(BLOCK_TYPE);
                String typeIn = findInputPort(ASSIGN, "input_block_type", null);
                if (blockOut != null && typeIn != null) {
                    builder.connect(blockRef, blockOut, toRef, typeIn);
                }
                String coordsIn = findInputPort(ASSIGN, "input_coordinates", null);
                if (coordsIn != null) {
                    inPort = coordsIn;
                }
            }
            if (PREVIEW_BLOCKS.equals(toType)) {
                String placementsIn = findInputPort(PREVIEW_BLOCKS, "input_block_placements", null);
                if (placementsIn != null) {
                    inPort = placementsIn;
                }
            }
            if (VOXELIZE.equals(toType)) {
                String geomIn = findInputPort(VOXELIZE, "input_geometry", null);
                if (geomIn != null) {
                    inPort = geomIn;
                }
            }
            WireResult wire = wireWithConversion(builder, currentRef, outPort, toRef, inPort, catalog, request);
            if (wire.failed()) {
                return ApplyPathResult.fail(wire.message());
            }
            currentRef = toRef;
            if (VOXELIZE.equals(toType) || SURFACE_STRIP_TO_BLOCKS.equals(toType)) {
                currentPort = findOutputPort(toType, "output_blocks", NodeDataType.BLOCK_LIST);
            } else if (ASSIGN.equals(toType)) {
                currentPort = findOutputPort(ASSIGN, "output_placements", null);
            } else {
                currentPort = primaryOutputPort(toType);
            }
        }
        return ApplyPathResult.ok(currentRef, currentPort);
    }

    private static boolean appendApply(PlanBuilder builder, String fromRef, String fromPort, List<String> reasons) {
        if (!builder.addNode(APPLY, builder.depth() + 1, 200)) {
            return false;
        }
        String applyRef = builder.refForType(APPLY);
        String inPort = findInputPort(APPLY, "input_block_placements", null);
        if (inPort == null) {
            inPort = findInputPort(APPLY, null, null);
        }
        // Prefer Assign placements (parallel to Preview), not Preview → Apply.
        String assignRef = builder.refForType(ASSIGN);
        String placementsOut = findOutputPort(ASSIGN, "output_placements", null);
        String srcRef = assignRef != null ? assignRef : fromRef;
        String srcPort = assignRef != null && placementsOut != null ? placementsOut : fromPort;
        builder.connect(srcRef, srcPort, applyRef, inPort);
        reasons.add("Added Apply Changes because world-write intent was explicit");
        return true;
    }

    private static WireResult wireWithConversion(
            PlanBuilder builder,
            String sourceRef,
            String sourcePortId,
            String targetRef,
            String targetPortId,
            NodeSemanticCatalog catalog,
            AiComposeRequest request
    ) {
        if (sourcePortId == null || targetPortId == null) {
            return WireResult.fail("Missing port ids for wiring.");
        }
        NodeDataType outType = portDataType(builder.typeForRef(sourceRef), sourcePortId, true);
        NodeDataType inType = portDataType(builder.typeForRef(targetRef), targetPortId, false);
        if (outType == null || inType == null) {
            builder.connect(sourceRef, sourcePortId, targetRef, targetPortId);
            return WireResult.ok();
        }
        TypeConversionRegistry.ConversionPolicy policy = catalog.conversionBetween(outType, inType);
        if (policy == TypeConversionRegistry.ConversionPolicy.IMPLICIT_SAFE
                || outType == inType) {
            builder.connect(sourceRef, sourcePortId, targetRef, targetPortId);
            return WireResult.ok();
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
        if (!builder.addNode(suggestion.nodeId(), builder.depth(), 40)) {
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
        return WireResult.ok();
    }

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
        if (validation.errors() != null && !validation.errors().isEmpty() && registryHasAny(builder)) {
            // Params use registry defaults — "Required input not connected" is expected for v1.
            List<String> hardErrors = validation.errors().stream()
                    .filter(e -> e != null && !e.startsWith("Required input not connected:"))
                    .toList();
            if (!hardErrors.isEmpty()) {
                return AiComposeResult.abstain(
                        AiComposeResult.ABSTAIN_CODE,
                        "Plan validation failed: " + hardErrors,
                        Set.of(),
                        Set.of()
                );
            }
        }

        Set<NodeCapability> present = EnumSet.noneOf(NodeCapability.class);
        for (AiPlanNode node : plan.nodes()) {
            present.addAll(catalog.capabilities(node.typeId()));
        }
        Set<NodeCapability> required = EnumSet.copyOf(request.requiredCapabilities());
        // Composer always satisfies PREVIEW for PREVIEW/WORLD goals unless CAPABILITY_SET.
        if (request.goal() != AiComposeGoal.CAPABILITY_SET) {
            required.add(NodeCapability.PREVIEW);
        }
        if (request.goal() == AiComposeGoal.WORLD_OUTPUT) {
            required.add(NodeCapability.WORLD_APPLY);
        }
        Set<NodeCapability> missing = EnumSet.noneOf(NodeCapability.class);
        for (NodeCapability cap : required) {
            if (!present.contains(cap)
                    && cap != NodeCapability.APPLY) {
                // WORLD_APPLY may be satisfied by Apply node; APPLY is alias.
                if (cap == NodeCapability.WORLD_APPLY && present.contains(NodeCapability.APPLY)) {
                    continue;
                }
                if (cap == NodeCapability.WORLD_APPLY && request.goal() != AiComposeGoal.WORLD_OUTPUT) {
                    continue;
                }
                missing.add(cap);
            }
        }
        // Soften: SPHERE/BOX/CURVE etc. are seed markers — if seed present, covered.
        missing.removeIf(cap -> seedCovers(cap, builder));

        if (!missing.isEmpty() && request.goal() != AiComposeGoal.CAPABILITY_SET) {
            // Still allow if only soft caps missing and we have PREVIEW.
            EnumSet<NodeCapability> hard = EnumSet.copyOf(missing);
            hard.remove(NodeCapability.SPHERE);
            hard.remove(NodeCapability.BOX);
            hard.remove(NodeCapability.CURVE);
            hard.remove(NodeCapability.ROOF);
            hard.remove(NodeCapability.TERRAIN);
            hard.remove(NodeCapability.SDF);
            hard.remove(NodeCapability.FIELD);
            if (!hard.isEmpty() && !present.contains(NodeCapability.PREVIEW)
                    && request.goal() == AiComposeGoal.PREVIEW) {
                return AiComposeResult.abstain(
                        AiComposeResult.ABSTAIN_CODE,
                        "Missing capabilities: " + hard,
                        present,
                        hard
                );
            }
            if (!hard.isEmpty()) {
                // Architecture/cut must be present when required.
                if (hard.contains(NodeCapability.BOOLEAN_CUT) || hard.contains(NodeCapability.WALL)
                        || hard.contains(NodeCapability.WINDOW) || hard.contains(NodeCapability.PREVIEW)) {
                    if (!present.containsAll(hard)) {
                        EnumSet<NodeCapability> still = EnumSet.copyOf(hard);
                        still.removeIf(present::contains);
                        if (!still.isEmpty()) {
                            return AiComposeResult.abstain(
                                    AiComposeResult.ABSTAIN_CODE,
                                    "Missing capabilities: " + still,
                                    present,
                                    still
                            );
                        }
                    }
                }
            }
        }

        return AiComposeResult.success(plan, present, reasons, cost);
    }

    private static boolean seedCovers(NodeCapability cap, PlanBuilder builder) {
        return switch (cap) {
            case SPHERE -> builder.hasType(SPHERE);
            case BOX -> builder.hasType(BOX);
            case WALL -> builder.hasType(WALL);
            case WINDOW, OPENING, ARRAY -> builder.hasType(WINDOW_ARRAY);
            case SWEEP -> builder.hasType(SWEEP);
            case EXTRUDE -> builder.hasType(EXTRUDE_REGION);
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

    // --- Seed selection ---

    static List<String> selectSeeds(AiComposeRequest request, NodeSemanticCatalog catalog) {
        if (request.seedNodeIds() != null && !request.seedNodeIds().isEmpty()) {
            return List.copyOf(request.seedNodeIds());
        }
        Set<NodeCapability> caps = request.requiredCapabilities();
        List<ScoredSeed> scored = new ArrayList<>();

        if (caps.contains(NodeCapability.WALL)) {
            scored.add(new ScoredSeed(WALL, 100));
        }
        if (caps.contains(NodeCapability.WINDOW) || caps.contains(NodeCapability.OPENING)) {
            int score = 90;
            if (caps.contains(NodeCapability.ARRAY) || caps.contains(NodeCapability.BOOLEAN_CUT)) {
                score += 20;
            }
            scored.add(new ScoredSeed(WINDOW_ARRAY, score));
        }
        if (caps.contains(NodeCapability.SPHERE) || containsAny(request.prompt(), "球", "sphere")) {
            scored.add(new ScoredSeed(SPHERE, 95));
        }
        if (caps.contains(NodeCapability.BOX) || containsAny(request.prompt(), "盒子", "box", "cube")) {
            scored.add(new ScoredSeed(BOX, 80));
        }
        if (caps.contains(NodeCapability.SWEEP)
                || containsAny(request.prompt(), "sweep", "扫掠", "放样", "helix", "螺旋", "arch", "拱")) {
            scored.add(new ScoredSeed(HELIX_PATH, 88));
            scored.add(new ScoredSeed(SWEEP, 85));
        }
        if (caps.contains(NodeCapability.EXTRUDE) || containsAny(request.prompt(), "extrude", "拉伸")) {
            scored.add(new ScoredSeed(RECT_PROFILE, 70));
        }
        // Prefer window_array when WALL+WINDOW+BOOLEAN_CUT (array/opening preference).
        if (caps.contains(NodeCapability.WALL)
                && (caps.contains(NodeCapability.WINDOW) || caps.contains(NodeCapability.OPENING))
                && (caps.contains(NodeCapability.BOOLEAN_CUT) || caps.contains(NodeCapability.ARRAY))) {
            scored.removeIf(s -> WINDOW_ARRAY.equals(s.typeId()));
            scored.add(new ScoredSeed(WINDOW_ARRAY, 120));
        }

        scored.sort(Comparator.comparingInt(ScoredSeed::score).reversed());
        LinkedHashSet<String> seeds = new LinkedHashSet<>();
        for (ScoredSeed seed : scored) {
            seeds.add(seed.typeId());
            if (seeds.size() >= 3) {
                break;
            }
        }
        // Wall+window pair
        if (caps.contains(NodeCapability.WALL) && (caps.contains(NodeCapability.WINDOW) || caps.contains(NodeCapability.OPENING))) {
            seeds.add(WALL);
            seeds.add(WINDOW_ARRAY);
        }
        return List.copyOf(seeds);
    }

    // --- Effect / edge policy ---

    static boolean isEffectAllowed(NodeEffect effect, boolean allowWorldWrite) {
        if (effect == null || effect == NodeEffect.UNSPECIFIED || effect == NodeEffect.PURE
                || effect == NodeEffect.CONTEXT_READ || effect == NodeEffect.WORLD_READ
                || effect == NodeEffect.PREVIEW_WRITE || effect == NodeEffect.COMPOSITE
                || effect == NodeEffect.EDITOR_ONLY) {
            return true;
        }
        if (effect == NodeEffect.WORLD_WRITE || effect == NodeEffect.CONTEXT_WRITE) {
            return allowWorldWrite;
        }
        return false; // FILE_IO, NETWORK, UI_EFFECT blocked
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
        // Skip GetBoxFace orientation variants unless caller-supplied (v1: omit).
        if (edge.sourcePortId() != null && edge.sourcePortId().contains(":")) {
            return false;
        }
        // Scalar silence: no CATEGORY/TYPE expansion from silent scalars / param-like ports.
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
        if (!isEffectAllowed(catalog.effect(edge.targetNodeId()), allowWorldWrite)) {
            return false;
        }
        return true;
    }

    private static boolean isPreviewTerminal(String typeId) {
        return typeId != null && typeId.startsWith("output.preview.");
    }

    private static boolean isUnsupportedDomainHeavy(Set<NodeDomain> domains) {
        if (domains == null || domains.isEmpty()) {
            return false;
        }
        boolean heavy = domains.contains(NodeDomain.SDF)
                || domains.contains(NodeDomain.FIELD)
                || domains.contains(NodeDomain.TERRAIN);
        boolean supported = false;
        for (NodeDomain d : domains) {
            if (SUPPORTED_DOMAINS.contains(d)) {
                supported = true;
                break;
            }
        }
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
                || caps.contains(NodeCapability.PREVIEW);
    }

    private static String visitKey(String typeId, String port) {
        return Objects.toString(typeId, "") + "|" + Objects.toString(port, "");
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

    // --- Port helpers ---

    private static final List<String> PREFERRED_OUTPUTS = List.of(
            "output_geometry",
            "output_profile",
            "output_path",
            "output_surface_strip",
            "output_openings",
            "output_blocks",
            "output_placements",
            "output_block_placements",
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

    private static boolean hasGeometryPrimaryOutput(String typeId) {
        INode node = tryCreate(typeId);
        if (node == null || node.getOutputPorts() == null) {
            return typeId != null && (typeId.contains("primitives") || typeId.contains("boolean")
                    || typeId.contains("architectural") || typeId.contains("solids"));
        }
        for (IPort port : node.getOutputPorts()) {
            if (port != null && port.getDataType() == NodeDataType.GEOMETRY) {
                return true;
            }
        }
        return false;
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
            for (IPort port : node.getInputPorts()) {
                if (port != null && port.getDataType() != NodeDataType.EXEC && port.isRequired()) {
                    return port.getId();
                }
            }
            for (IPort port : node.getInputPorts()) {
                if (port != null && port.getDataType() != NodeDataType.EXEC) {
                    return port.getId();
                }
            }
        }
        return preferredId;
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
        return findInputPort(typeId, null, null);
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
        if (typeId.contains("voxelize")) {
            return "output_blocks";
        }
        if (typeId.contains("assign_block")) {
            return "output_placements";
        }
        if (typeId.contains("preview_blocks")) {
            return "output_status";
        }
        if (typeId.contains("window_array")) {
            return "output_openings";
        }
        return "output_geometry";
    }

    // --- Builder ---

    private static final class PlanBuilder {
        private final int maxNodes;
        private final Map<String, String> typeToRef = new HashMap<>();
        private final Map<String, String> refToType = new HashMap<>();
        private final List<AiPlanNode> nodes = new ArrayList<>();
        private final List<AiPlanConnection> connections = new ArrayList<>();
        private int seq;
        private int maxDepth;

        PlanBuilder(int maxNodes) {
            this.maxNodes = maxNodes;
        }

        int depth() {
            return maxDepth;
        }

        boolean addSeed(String typeId, int depth, float y) {
            return addNode(typeId, depth, y);
        }

        boolean addNode(String typeId, int depth, float y) {
            if (typeToRef.containsKey(typeId.toLowerCase(Locale.ROOT))) {
                return true;
            }
            if (nodes.size() >= maxNodes) {
                return false;
            }
            String ref = "n" + (++seq);
            typeToRef.put(typeId.toLowerCase(Locale.ROOT), ref);
            refToType.put(ref, typeId);
            nodes.add(new AiPlanNode(ref, typeId, depth * 280.0f, y, null));
            maxDepth = Math.max(maxDepth, depth);
            return true;
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
            connections.add(new AiPlanConnection(fromRef, fromPort, toRef, toPort));
        }

        AiGraphPlan toPlan(String summary) {
            return new AiGraphPlan(summary, List.copyOf(nodes), List.copyOf(connections), List.of());
        }
    }

    private record ScoredSeed(String typeId, int score) {
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

    private record ApplyPathResult(String terminalRef, String terminalOutPort, String abstainMessage) {
        static ApplyPathResult ok(String ref, String port) {
            return new ApplyPathResult(ref, port, null);
        }

        static ApplyPathResult fail(String message) {
            return new ApplyPathResult(null, null, message);
        }
    }

    private record WireResult(String message) {
        static WireResult ok() {
            return new WireResult(null);
        }

        static WireResult fail(String message) {
            return new WireResult(message);
        }

        boolean failed() {
            return message != null;
        }
    }
}
