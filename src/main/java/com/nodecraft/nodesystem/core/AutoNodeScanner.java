package com.nodecraft.nodesystem.core;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.lang.Deprecated;
import java.lang.reflect.Modifier;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Scans the canonical node package and registers discovered node classes.
 */
public final class AutoNodeScanner {

    private static final String BASE_PACKAGE = "com.nodecraft.nodesystem.nodes";
    private static final String BASE_PACKAGE_PATH = "com/nodecraft/nodesystem/nodes";

    private AutoNodeScanner() {
    }

    /**
     * Scans and registers all node classes under the canonical node package.
     *
     * @param registry target registry
     * @return number of successfully registered nodes
     */
    public static int scanAndRegisterNodes(NodeRegistry registry) {
        NodeCraft.LOGGER.debug("Starting automatic node scan...");
        int count = 0;

        try {
            ClassLoader classLoader = AutoNodeScanner.class.getClassLoader();
            Enumeration<URL> resources = classLoader.getResources(BASE_PACKAGE_PATH);

            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String protocol = resource.getProtocol();

                if ("file".equals(protocol)) {
                    count += scanFileSystem(registry, resource);
                } else if ("jar".equals(protocol)) {
                    count += scanJar(registry, resource);
                } else {
                    NodeCraft.LOGGER.warn("Unsupported resource protocol while scanning nodes: {}", protocol);
                }
            }
        } catch (IOException e) {
            NodeCraft.LOGGER.error("I/O error while scanning nodes: {}", e.getMessage(), e);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Unexpected error while scanning nodes: {}", e.getMessage(), e);
        }

        NodeCraft.LOGGER.info("Automatic node scan completed. Registered {} nodes.", count);
        return count;
    }

    private static int scanFileSystem(NodeRegistry registry, URL resource) {
        int count = 0;
        try {
            Path basePath = Paths.get(resource.toURI());
            List<Path> classFiles = Files.walk(basePath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".class"))
                .toList();

            for (Path classFile : classFiles) {
                String className = getClassNameFromPath(basePath, classFile);
                if (processClass(registry, className)) {
                    count++;
                }
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to scan node classes from file system: {}", e.getMessage(), e);
        }
        return count;
    }

    private static int scanJar(NodeRegistry registry, URL resource) {
        int count = 0;
        try {
            JarURLConnection jarConnection = (JarURLConnection) resource.openConnection();
            JarFile jarFile = jarConnection.getJarFile();

            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (!entryName.startsWith(BASE_PACKAGE_PATH) || !entryName.endsWith(".class")) {
                    continue;
                }

                String className = entryName.replace('/', '.').substring(0, entryName.length() - 6);
                if (processClass(registry, className)) {
                    count++;
                }
            }
        } catch (IOException e) {
            NodeCraft.LOGGER.error("Failed to scan node classes from JAR: {}", e.getMessage(), e);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Unexpected JAR scanning error: {}", e.getMessage(), e);
        }
        return count;
    }

    private static String getClassNameFromPath(Path basePath, Path classPath) {
        String relativePath = basePath.relativize(classPath).toString();
        String className = relativePath.replace(File.separatorChar, '.')
            .replace('/', '.')
            .replace('\\', '.');

        if (className.endsWith(".class")) {
            className = className.substring(0, className.length() - 6);
        }

        return BASE_PACKAGE + "." + className;
    }

    private static boolean processClass(NodeRegistry registry, String className) {
        try {
            Class<?> clazz = Class.forName(className);
            if (!INode.class.isAssignableFrom(clazz)) {
                return false;
            }
            if (Modifier.isAbstract(clazz.getModifiers())) {
                return false;
            }
            if (clazz.isAnnotationPresent(Deprecated.class)) {
                return false;
            }

            NodeInfo annotation = clazz.getAnnotation(NodeInfo.class);
            if (annotation == null) {
                return registerNodeByConvention(registry, clazz);
            }
            return registerNodeByAnnotation(registry, clazz, annotation);
        } catch (ClassNotFoundException e) {
            NodeCraft.LOGGER.debug("Node class could not be loaded during scanning: {}", className);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to process scanned class {}: {}", className, e.getMessage(), e);
        }

        return false;
    }

    private static boolean registerNodeByAnnotation(NodeRegistry registry, Class<?> nodeClass, NodeInfo annotation) {
        try {
            String id = annotation.id();
            String displayName = annotation.displayName();
            String description = annotation.description();
            String category = annotation.category();
            int order = annotation.order();
            String icon = annotation.icon();

            if (id.isEmpty()) {
                id = nodeClass.getSimpleName().toLowerCase();
                if (!id.endsWith("node")) {
                    id += "_node";
                }
            }

            if (displayName.isEmpty()) {
                displayName = formatClassName(nodeClass.getSimpleName());
            }

            if (category.isEmpty()) {
                NodeCraft.LOGGER.warn("Annotated node {} has no category.", nodeClass.getName());
                return false;
            }

            if (!validateAnnotatedNodeMetadata(nodeClass, id, category)) {
                return false;
            }

            @SuppressWarnings("unchecked")
            Class<? extends INode> castedClass = (Class<? extends INode>) nodeClass;
            com.nodecraft.gui.node.NodeInfo nodeInfo =
                new com.nodecraft.gui.node.NodeInfo(id, displayName, description, category, order, castedClass);
            if (icon != null && !icon.isBlank()) {
                nodeInfo.setIcon(icon.trim());
            }

            boolean success = registry.registerNode(nodeInfo);
            if (success && !validateRegisteredNodeMetadata(registry, id, category, castedClass)) {
                NodeCraft.LOGGER.error("Annotated node {} failed post-registration validation.", nodeClass.getName());
                return false;
            }

            if (!success) {
                NodeCraft.LOGGER.warn("Automatic registration failed for node {} ({})", displayName, id);
            }

            return success;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to register annotated node {}: {}", nodeClass.getName(), e.getMessage(), e);
            return false;
        }
    }

    private static boolean validateAnnotatedNodeMetadata(Class<?> nodeClass, String annotatedId, String annotatedCategory) {
        try {
            Object instance = nodeClass.getDeclaredConstructor().newInstance();
            if (!(instance instanceof INode nodeInstance)) {
                NodeCraft.LOGGER.error("Annotated node class {} did not instantiate as INode.", nodeClass.getName());
                return false;
            }

            String normalizedAnnotationId = annotatedId == null ? "" : annotatedId.toLowerCase();
            String normalizedTypeId = nodeInstance.getTypeId() == null ? "" : nodeInstance.getTypeId().toLowerCase();
            if (!normalizedAnnotationId.equals(normalizedTypeId)) {
                NodeCraft.LOGGER.error(
                    "Node metadata mismatch for {}: annotation id '{}' does not match runtime typeId '{}'.",
                    nodeClass.getName(), annotatedId, nodeInstance.getTypeId()
                );
                return false;
            }

            if (annotatedCategory == null || annotatedCategory.isBlank()) {
                NodeCraft.LOGGER.error("Node {} declares a blank category.", nodeClass.getName());
                return false;
            }

            return true;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to validate annotated node {} before registration: {}", nodeClass.getName(), e.getMessage(), e);
            return false;
        }
    }

    private static boolean validateRegisteredNodeMetadata(NodeRegistry registry,
                                                          String annotatedId,
                                                          String annotatedCategory,
                                                          Class<? extends INode> nodeClass) {
        com.nodecraft.gui.node.NodeInfo registeredInfo = registry.getNodeInfo(annotatedId);
        if (registeredInfo == null) {
            NodeCraft.LOGGER.error("Node {} was not found in the registry after registration.", annotatedId);
            return false;
        }

        String normalizedCategory = annotatedCategory == null ? "" : annotatedCategory.toLowerCase();
        if (!normalizedCategory.equals(registeredInfo.getCategoryId())) {
            NodeCraft.LOGGER.error(
                "Node registration mismatch for {}: annotation category '{}' does not match registry category '{}'.",
                annotatedId, annotatedCategory, registeredInfo.getCategoryId()
            );
            return false;
        }

        if (!nodeClass.equals(registeredInfo.getNodeClass())) {
            NodeCraft.LOGGER.error(
                "Node registration mismatch for {}: registry class '{}' does not match scanned class '{}'.",
                annotatedId,
                registeredInfo.getNodeClass() != null ? registeredInfo.getNodeClass().getName() : "null",
                nodeClass.getName()
            );
            return false;
        }

        return true;
    }

    private static boolean registerNodeByConvention(NodeRegistry registry, Class<?> nodeClass) {
        try {
            String className = nodeClass.getSimpleName();
            String packageName = nodeClass.getPackage().getName();

            String category = extractCategoryFromPackage(packageName);
            if (category == null) {
                NodeCraft.LOGGER.warn("Could not infer category from package {}", packageName);
                return false;
            }

            String id = className.toLowerCase();
            if (!id.endsWith("node")) {
                id += "_node";
            }

            String displayName = formatClassName(className);

            @SuppressWarnings("unchecked")
            Class<? extends INode> castedClass = (Class<? extends INode>) nodeClass;
            com.nodecraft.gui.node.NodeInfo nodeInfo =
                new com.nodecraft.gui.node.NodeInfo(id, displayName, "", category, castedClass);

            return registry.registerNode(nodeInfo);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to register node by convention {}: {}", nodeClass.getName(), e.getMessage(), e);
            return false;
        }
    }

    private static String extractCategoryFromPackage(String packageName) {
        if (!packageName.startsWith(BASE_PACKAGE)) {
            return null;
        }

        String subPackage = packageName.substring(BASE_PACKAGE.length());
        if (subPackage.startsWith(".")) {
            subPackage = subPackage.substring(1);
        }

        String[] parts = subPackage.split("\\.");
        if (parts.length < 2) {
            return null;
        }

        return parts[0] + "." + parts[1];
    }

    private static String formatClassName(String className) {
        if (className.endsWith("Node")) {
            className = className.substring(0, className.length() - 4);
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < className.length(); i++) {
            char c = className.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                result.append(' ');
            }
            result.append(c);
        }

        return result.toString();
    }
}
