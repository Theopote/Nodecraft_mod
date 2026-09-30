package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.ListElementKind;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.PortTypeResolver;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.TreePathData;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed boundary helpers for data-tree nodes.
 * <p>
 * LIST to DATA_TREE coercion is not performed here — use Graft List / Flatten Tree.
 * Path ports accept {@link TreePathData} only (fail-closed; no string fallback to {@code {0}}).
 */
public final class DataTreeNodeUtils {

    public static final String ERROR_INVALID_INPUT = "invalid_input";
    public static final String ERROR_INVALID_PATH = "invalid_path";
    public static final String ERROR_NULL_ITEM = "null_item";
    public static final String ERROR_ELEMENT_KIND_MISMATCH = "element_kind_mismatch";
    public static final String ERROR_ELEMENT_KIND_CONFLICT = "element_kind_conflict";
    public static final String ERROR_PATH_DEPTH_EXCEEDED = "path_depth_exceeded";
    public static final String ERROR_INVALID_SHIFT = "invalid_shift";

    enum TreeInputState {
        SKIP,
        VALID,
        INVALID
    }

    record ConnectedTreeResult(@Nullable DataTreeData tree, TreeInputState state, @Nullable String error) {
    }

    private DataTreeNodeUtils() {
    }

    record ParseResult<T>(@Nullable T value, boolean valid, @Nullable String error) {
        static <T> ParseResult<T> ok(T value) {
            return new ParseResult<>(value, true, null);
        }

        static <T> ParseResult<T> invalid(String error) {
            return new ParseResult<>(null, false, error);
        }
    }

    static ParseResult<List<?>> parseList(Object value) {
        if (value == null || !(value instanceof List<?> list)) {
            return ParseResult.invalid(ERROR_INVALID_INPUT);
        }
        return ParseResult.ok(new ArrayList<>(list));
    }

    static ParseResult<DataTreeData> parseTree(Object value) {
        if (value == null || !(value instanceof DataTreeData tree)) {
            return ParseResult.invalid(ERROR_INVALID_INPUT);
        }
        return ParseResult.ok(tree);
    }

    static ParseResult<TreePathData> parsePath(Object value) {
        if (value == null || !(value instanceof TreePathData path)) {
            return ParseResult.invalid(ERROR_INVALID_PATH);
        }
        return ParseResult.ok(path);
    }

    static ParseResult<Void> validateNonNullItems(List<?> list) {
        for (Object item : list) {
            if (item == null) {
                return ParseResult.invalid(ERROR_NULL_ITEM);
            }
        }
        return ParseResult.ok(null);
    }

    static ParseResult<Void> validateItemsMatchKind(List<?> list, ListElementKind kind) {
        if (kind == null
                || kind == ListElementKind.UNCONSTRAINED
                || kind == ListElementKind.NONE
                || kind == ListElementKind.BLOCK_PLACEMENT) {
            return ParseResult.ok(null);
        }
        for (Object item : list) {
            if (!ListElementKindValidator.matches(item, kind)) {
                return ParseResult.invalid(ERROR_ELEMENT_KIND_MISMATCH);
            }
        }
        return ParseResult.ok(null);
    }

    static ParseResult<Void> validateTreeItemsMatchKind(DataTreeData tree, ListElementKind kind) {
        return validateItemsMatchKind(tree.flatten(), kind);
    }

    static ConnectedTreeResult resolveConnectedTree(BaseNode node, String portId) {
        if (!isTreePortDriven(node, portId)) {
            return new ConnectedTreeResult(null, TreeInputState.SKIP, null);
        }
        ParseResult<DataTreeData> parsed = parseTree(node.getInput(portId));
        if (!parsed.valid()) {
            return new ConnectedTreeResult(null, TreeInputState.INVALID, parsed.error());
        }
        return new ConnectedTreeResult(parsed.value(), TreeInputState.VALID, null);
    }

    static boolean isTreePortDriven(BaseNode node, String portId) {
        return OptionalPortDrive.isConnected(node, portId) || node.isInputPresent(portId);
    }

    static ParseResult<ListElementKind> mergeKindsStrict(ListElementKind a, ListElementKind b) {
        ListElementKind left = a == null ? ListElementKind.UNCONSTRAINED : a;
        ListElementKind right = b == null ? ListElementKind.UNCONSTRAINED : b;
        if (!isConstrained(left) && !isConstrained(right)) {
            return ParseResult.ok(ListElementKind.UNCONSTRAINED);
        }
        if (!isConstrained(left)) {
            return ParseResult.ok(right);
        }
        if (!isConstrained(right)) {
            return ParseResult.ok(left);
        }
        if (left == right) {
            return ParseResult.ok(left);
        }
        return ParseResult.invalid(ERROR_ELEMENT_KIND_CONFLICT);
    }

    static ParseResult<ListElementKind> foldKindsStrict(ListElementKind accumulated, ListElementKind next) {
        if (accumulated == null) {
            return ParseResult.ok(next == null ? ListElementKind.UNCONSTRAINED : next);
        }
        return mergeKindsStrict(accumulated, next);
    }

    static ParseResult<Void> preflightPathDepths(Iterable<List<Integer>> paths) {
        for (List<Integer> path : paths) {
            if (path != null && path.size() > GenerationLimits.MAX_TREE_PATH_DEPTH) {
                return ParseResult.invalid(ERROR_PATH_DEPTH_EXCEEDED);
            }
        }
        return ParseResult.ok(null);
    }

    static ParseResult<Void> preflightShiftPaths(DataTreeData tree, int shift) {
        for (DataTreeData.Branch branch : tree.getBranches()) {
            if (shiftedPathDepth(branch.path(), shift) > GenerationLimits.MAX_TREE_PATH_DEPTH) {
                return ParseResult.invalid(ERROR_PATH_DEPTH_EXCEEDED);
            }
        }
        return ParseResult.ok(null);
    }

    static int shiftedPathDepth(List<Integer> path, int shift) {
        if (shift == 0) {
            return path.size();
        }
        if (shift > 0) {
            return shift >= path.size() ? 0 : path.size() - shift;
        }
        long addedLevels = -(long) shift;
        long result = (long) path.size() + addedLevels;
        if (result > Integer.MAX_VALUE) {
            return GenerationLimits.MAX_TREE_PATH_DEPTH + 1;
        }
        return (int) result;
    }

    static List<Integer> shiftPath(List<Integer> path, int amount) {
        if (amount == 0) {
            return path;
        }
        if (amount > 0) {
            return amount >= path.size() ? List.of() : List.copyOf(path.subList(amount, path.size()));
        }
        long addedLevels = -(long) amount;
        int added = (int) addedLevels;
        List<Integer> shifted = new ArrayList<>(path.size() + added);
        for (int i = 0; i < added; i++) {
            shifted.add(0);
        }
        shifted.addAll(path);
        return List.copyOf(shifted);
    }

    static ParseResult<Void> preflightEntwinePaths(DataTreeData tree, int sourceIndex) {
        List<List<Integer>> paths = new ArrayList<>(tree.getBranchCount());
        for (DataTreeData.Branch branch : tree.getBranches()) {
            int depth = 1 + branch.path().size();
            if (depth > GenerationLimits.MAX_TREE_PATH_DEPTH) {
                return ParseResult.invalid(ERROR_PATH_DEPTH_EXCEEDED);
            }
            List<Integer> path = new ArrayList<>(depth);
            path.add(sourceIndex);
            path.addAll(branch.path());
            paths.add(path);
        }
        return preflightPathDepths(paths);
    }

    static DataTreeData requireTree(Object value) {
        if (value instanceof DataTreeData tree) {
            return tree;
        }
        return DataTreeData.empty();
    }

    static List<Object> requireList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    /**
     * @return path when value is a valid {@link TreePathData}; otherwise {@code null} (not found).
     */
    static TreePathData requirePath(Object value) {
        if (value instanceof TreePathData path) {
            return path;
        }
        return null;
    }

    /**
     * List index language: negatives from end; out of range → -1 (no wrap).
     */
    static int resolveIndex(int index, int size) {
        if (size <= 0) {
            return -1;
        }
        int resolved = index;
        if (resolved < 0) {
            resolved = size + resolved;
        }
        return resolved >= 0 && resolved < size ? resolved : -1;
    }

    static ListElementKind resolveElementKindFromListPort(BaseNode node, String listPortId) {
        for (IPort port : node.getInputPorts()) {
            if (port != null && listPortId.equals(port.getId())) {
                NodeDataType effective = PortTypeResolver.resolveEffectiveType(port);
                if (effective != null && effective.isListType()) {
                    return effective.getListElementKind();
                }
            }
        }
        return ListElementKind.UNCONSTRAINED;
    }

    static ListElementKind resolveElementKindFromTreePort(BaseNode node, String treePortId, Object treeValue) {
        ListElementKind fromBinding = kindFromSharedVariable(node, treePortId);
        if (isConstrained(fromBinding)) {
            return fromBinding;
        }
        if (treeValue instanceof DataTreeData tree) {
            return tree.getElementKind();
        }
        return ListElementKind.UNCONSTRAINED;
    }

    private static ListElementKind kindFromSharedVariable(BaseNode node, String treePortId) {
        String variable = null;
        for (IPort port : node.getInputPorts()) {
            if (port != null && treePortId.equals(port.getId())) {
                variable = port.getListTypeVariable();
                break;
            }
        }
        if (variable == null || variable.isBlank()) {
            return ListElementKind.UNCONSTRAINED;
        }
        for (IPort port : node.getInputPorts()) {
            if (port == null || !variable.equals(port.getListTypeVariable())) {
                continue;
            }
            if (port.getDataType() != null && port.getDataType().isListType()) {
                NodeDataType effective = PortTypeResolver.resolveEffectiveType(port);
                if (effective != null && isConstrained(effective.getListElementKind())) {
                    return effective.getListElementKind();
                }
            }
        }
        for (IPort port : node.getOutputPorts()) {
            if (port == null || !variable.equals(port.getListTypeVariable())) {
                continue;
            }
            if (port.getDataType() != null && port.getDataType().isListType()) {
                NodeDataType effective = PortTypeResolver.resolveEffectiveType(port);
                if (effective != null && isConstrained(effective.getListElementKind())) {
                    return effective.getListElementKind();
                }
            }
        }
        for (IPort port : node.getOutputPorts()) {
            if (port != null && variable.equals(port.getListTypeVariable())
                    && port.getDataType() != null && port.getDataType().isListType()) {
                NodeDataType effective = PortTypeResolver.resolveEffectiveType(port);
                if (effective != null && isConstrained(effective.getListElementKind())) {
                    return effective.getListElementKind();
                }
            }
        }
        for (IPort port : node.getInputPorts()) {
            if (port != null && variable.equals(port.getListTypeVariable())) {
                NodeDataType effective = PortTypeResolver.resolveEffectiveType(port);
                if (effective != null && effective.isListType() && isConstrained(effective.getListElementKind())) {
                    return effective.getListElementKind();
                }
            }
        }
        return ListElementKind.UNCONSTRAINED;
    }

    private static boolean isConstrained(ListElementKind kind) {
        return kind != null
                && kind != ListElementKind.UNCONSTRAINED
                && kind != ListElementKind.NONE;
    }
}
