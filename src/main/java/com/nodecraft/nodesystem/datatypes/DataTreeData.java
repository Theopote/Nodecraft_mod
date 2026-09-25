package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.api.ListElementKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Hierarchical list data with unique branch paths and optional element kind.
 * <p>
 * Construction merges duplicate paths by appending items in encounter order
 * (one path = one branch). Element kind preserves {@code DataTree&lt;T&gt;} across
 * Graft / Flatten without exploding into per-kind tree port types.
 */
public class DataTreeData {
    private final ListElementKind elementKind;
    private final List<Branch> branches;

    public DataTreeData(List<Branch> branches) {
        this(branches, ListElementKind.UNCONSTRAINED);
    }

    public DataTreeData(List<Branch> branches, ListElementKind elementKind) {
        Objects.requireNonNull(branches, "Data tree branches cannot be null");
        this.elementKind = elementKind == null ? ListElementKind.UNCONSTRAINED : elementKind;

        Map<List<Integer>, List<Object>> merged = new LinkedHashMap<>();
        for (Branch branch : branches) {
            Objects.requireNonNull(branch, "Branch cannot be null");
            List<Integer> path = List.copyOf(branch.path());
            List<Object> items = merged.computeIfAbsent(path, ignored -> new ArrayList<>());
            items.addAll(branch.items());
        }

        List<Branch> canonical = new ArrayList<>(merged.size());
        for (Map.Entry<List<Integer>, List<Object>> entry : merged.entrySet()) {
            canonical.add(new Branch(entry.getKey(), entry.getValue()));
        }
        canonical.sort(Comparator.comparing(Branch::pathKey));
        this.branches = List.copyOf(canonical);
    }

    public static DataTreeData empty() {
        return new DataTreeData(List.of(), ListElementKind.UNCONSTRAINED);
    }

    public static DataTreeData empty(ListElementKind elementKind) {
        return new DataTreeData(List.of(), elementKind);
    }

    public static DataTreeData fromBranches(List<List<Integer>> paths, List<List<?>> items) {
        return fromBranches(paths, items, ListElementKind.UNCONSTRAINED);
    }

    public static DataTreeData fromBranches(List<List<Integer>> paths, List<List<?>> items,
                                           ListElementKind elementKind) {
        if (paths.size() != items.size()) {
            throw new IllegalArgumentException("Data tree paths and item lists must have the same size");
        }
        List<Branch> branches = new ArrayList<>(paths.size());
        for (int i = 0; i < paths.size(); i++) {
            branches.add(new Branch(paths.get(i), new ArrayList<>(items.get(i))));
        }
        return new DataTreeData(branches, elementKind);
    }

    /**
     * Rebuilds a tree preserving {@link #getElementKind()} while applying a new branch set.
     */
    public DataTreeData withBranches(List<Branch> newBranches) {
        return new DataTreeData(newBranches, elementKind);
    }

    public ListElementKind getElementKind() {
        return elementKind;
    }

    public List<Branch> getBranches() {
        return branches;
    }

    public List<Object> flatten() {
        List<Object> flattened = new ArrayList<>();
        for (Branch branch : branches) {
            flattened.addAll(branch.items());
        }
        return List.copyOf(flattened);
    }

    public Branch getBranch(List<Integer> path) {
        for (Branch branch : branches) {
            if (branch.path().equals(path)) {
                return branch;
            }
        }
        return null;
    }

    public Branch getBranch(TreePathData path) {
        return path == null ? null : getBranch(path.indices());
    }

    public int getBranchCount() {
        return branches.size();
    }

    public int getItemCount() {
        int count = 0;
        for (Branch branch : branches) {
            count += branch.items().size();
        }
        return count;
    }

    public int getMaxDepth() {
        int maxDepth = 0;
        for (Branch branch : branches) {
            maxDepth = Math.max(maxDepth, branch.path().size());
        }
        return maxDepth;
    }

    public List<List<Integer>> getPaths() {
        List<List<Integer>> paths = new ArrayList<>(branches.size());
        for (Branch branch : branches) {
            paths.add(branch.path());
        }
        return List.copyOf(paths);
    }

    public List<TreePathData> getTreePaths() {
        List<TreePathData> paths = new ArrayList<>(branches.size());
        for (Branch branch : branches) {
            paths.add(new TreePathData(branch.path()));
        }
        return List.copyOf(paths);
    }

    public String describe() {
        StringBuilder builder = new StringBuilder();
        builder.append("Data Tree: ")
            .append(getBranchCount())
            .append(" branches, ")
            .append(getItemCount())
            .append(" items, depth ")
            .append(getMaxDepth());
        if (elementKind != ListElementKind.UNCONSTRAINED && elementKind != ListElementKind.NONE) {
            builder.append(", kind ").append(elementKind);
        }
        for (Branch branch : branches) {
            builder.append('\n')
                .append(formatPath(branch.path()))
                .append(": ")
                .append(branch.items().size())
                .append(" items");
        }
        return builder.toString();
    }

    /**
     * Debug / legacy string parse only. Graph nodes must accept {@link TreePathData}.
     */
    public static List<Integer> parsePath(Object value) {
        if (value instanceof TreePathData treePath) {
            return treePath.indices();
        }
        if (value instanceof Number number) {
            return List.of(number.intValue());
        }
        if (value instanceof List<?> list) {
            List<Integer> path = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof Number number) {
                    path.add(number.intValue());
                }
            }
            return List.copyOf(path);
        }
        if (value instanceof String string) {
            String trimmed = string.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
            }
            if (trimmed.isBlank()) {
                return List.of();
            }
            String[] parts = trimmed.split("[;,/\\\\.]");
            List<Integer> path = new ArrayList<>(parts.length);
            for (String part : parts) {
                String token = part.trim();
                if (!token.isEmpty()) {
                    path.add(Integer.parseInt(token));
                }
            }
            return List.copyOf(path);
        }
        return List.of();
    }

    public static String formatPath(List<Integer> path) {
        StringBuilder builder = new StringBuilder("{");
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) {
                builder.append(';');
            }
            builder.append(path.get(i));
        }
        return builder.append('}').toString();
    }

    public record Branch(List<Integer> path, List<Object> items) {
        public Branch {
            Objects.requireNonNull(path, "Branch path cannot be null");
            Objects.requireNonNull(items, "Branch items cannot be null");
            path = List.copyOf(path);
            items = List.copyOf(items);
        }

        private String pathKey() {
            StringBuilder builder = new StringBuilder();
            for (Integer index : path) {
                builder.append(String.format("%010d", index)).append('/');
            }
            return builder.toString();
        }
    }
}
