package com.nodecraft.nodesystem.datatypes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Lightweight hierarchical list data for parameterized modeling workflows.
 * A branch path is an ordered integer address such as {0} or {2;4;1}.
 */
public class DataTreeData {
    private final List<Branch> branches;

    public DataTreeData(List<Branch> branches) {
        Objects.requireNonNull(branches, "Data tree branches cannot be null");
        List<Branch> copied = new ArrayList<>(branches.size());
        for (Branch branch : branches) {
            copied.add(new Branch(branch.path(), branch.items()));
        }
        copied.sort(Comparator.comparing(Branch::pathKey));
        this.branches = List.copyOf(copied);
    }

    public static DataTreeData empty() {
        return new DataTreeData(List.of());
    }

    public static DataTreeData fromBranches(List<List<Integer>> paths, List<List<?>> items) {
        if (paths.size() != items.size()) {
            throw new IllegalArgumentException("Data tree paths and item lists must have the same size");
        }
        List<Branch> branches = new ArrayList<>(paths.size());
        for (int i = 0; i < paths.size(); i++) {
            branches.add(new Branch(paths.get(i), new ArrayList<>(items.get(i))));
        }
        return new DataTreeData(branches);
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

    public String describe() {
        StringBuilder builder = new StringBuilder();
        builder.append("Data Tree: ")
            .append(getBranchCount())
            .append(" branches, ")
            .append(getItemCount())
            .append(" items, depth ")
            .append(getMaxDepth());
        for (Branch branch : branches) {
            builder.append('\n')
                .append(formatPath(branch.path()))
                .append(": ")
                .append(branch.items().size())
                .append(" items");
        }
        return builder.toString();
    }

    public static List<Integer> parsePath(Object value) {
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
