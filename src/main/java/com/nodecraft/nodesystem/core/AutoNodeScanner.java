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
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 自动节点扫描器
 * 扫描指定包路径下的所有实现INode接口的类，并自动注册为节点
 */
public class AutoNodeScanner {
    
    private static final String BASE_PACKAGE = "com.nodecraft.nodesystem.nodes";
    private static final String BASE_PACKAGE_PATH = "com/nodecraft/nodesystem/nodes";
    
    /**
     * 扫描并注册所有节点
     * @param registry 节点注册表
     * @return 注册的节点数量
     */
    public static int scanAndRegisterNodes(NodeRegistry registry) {
        NodeCraft.LOGGER.debug("开始自动扫描节点类...");
        int count = 0;
        
        try {
            // 获取基础包的资源
            ClassLoader classLoader = AutoNodeScanner.class.getClassLoader();
            Enumeration<URL> resources = classLoader.getResources(BASE_PACKAGE_PATH);
            
            // 处理所有找到的资源
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String protocol = resource.getProtocol();
                
                if ("file".equals(protocol)) {
                    // 处理文件系统中的资源
                    count += scanFileSystem(registry, resource);
                } else if ("jar".equals(protocol)) {
                    // 处理JAR包中的资源
                    count += scanJar(registry, resource);
                } else {
                    NodeCraft.LOGGER.warn("不支持的资源协议: {}", protocol);
                }
            }
            
        } catch (IOException e) {
            NodeCraft.LOGGER.error("扫描节点时发生IO错误: {}", e.getMessage(), e);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("扫描节点时发生未知错误: {}", e.getMessage(), e);
        }
        
        NodeCraft.LOGGER.info("节点自动扫描完成，共注册 {} 个节点", count);
        return count;
    }
    
    /**
     * 扫描文件系统中的类
     */
    private static int scanFileSystem(NodeRegistry registry, URL resource) {
        int count = 0;
        try {
            Path basePath = Paths.get(resource.toURI());
            List<Path> paths = Files.walk(basePath)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".class"))
                    .toList();
            
            for (Path path : paths) {
                String className = getClassNameFromPath(basePath, path);
                boolean registered = processClass(registry, className);
                if (registered) {
                    count++;
                }
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("扫描文件系统时出错: {}", e.getMessage(), e);
        }
        return count;
    }
    
    /**
     * 扫描 JAR 包中的类
     */
    private static int scanJar(NodeRegistry registry, URL resource) {
        int count = 0;
        try {
            JarURLConnection jarConnection = (JarURLConnection) resource.openConnection();
            JarFile jarFile = jarConnection.getJarFile();
            
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                // 只处理指定包路径下的 .class 文件
                if (entryName.startsWith(BASE_PACKAGE_PATH) && entryName.endsWith(".class")) {
                    // 将路径转换为类名: com/nodecraft/nodesystem/nodes/Foo.class → com.nodecraft.nodesystem.nodes.Foo
                    String className = entryName.replace('/', '.')
                            .substring(0, entryName.length() - 6); // 移除 .class
                    
                    boolean registered = processClass(registry, className);
                    if (registered) {
                        count++;
                    }
                }
            }
        } catch (IOException e) {
            NodeCraft.LOGGER.error("扫描JAR包时出错: {}", e.getMessage(), e);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("扫描JAR包时发生未知错误: {}", e.getMessage(), e);
        }
        return count;
    }
    
    /**
     * 从文件路径获取类名
     */
    private static String getClassNameFromPath(Path basePath, Path classPath) {
        String relativePath = basePath.relativize(classPath).toString();
        // 转换路径分隔符为点，并移除.class后缀
        String className = relativePath.replace(File.separatorChar, '.')
                .replace('/', '.')
                .replace('\\', '.');
                
        if (className.endsWith(".class")) {
            className = className.substring(0, className.length() - 6);
        }
        
        return BASE_PACKAGE + "." + className;
    }
    
    /**
     * 处理单个类
     */
    private static boolean processClass(NodeRegistry registry, String className) {
        try {
            Class<?> clazz = Class.forName(className);
            
            // 检查是否实现了INode接口
            if (!INode.class.isAssignableFrom(clazz)) {
                return false;
            }
            
            // 检查是否有NodeInfo注解
            NodeInfo annotation = clazz.getAnnotation(NodeInfo.class);
            if (annotation == null) {
                // 如果没有注解，则尝试从类名和包路径推断信息
                return registerNodeByConvention(registry, clazz);
            } else {
                // 如果有注解，则使用注解信息注册节点
                return registerNodeByAnnotation(registry, clazz, annotation);
            }
            
        } catch (ClassNotFoundException e) {
            NodeCraft.LOGGER.debug("类加载失败: {}", className);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("处理类 {} 时出错: {}", className, e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 根据注解注册节点
     */
    private static boolean registerNodeByAnnotation(NodeRegistry registry, Class<?> nodeClass, NodeInfo annotation) {
        try {
            // 获取注解中的信息
            String id = annotation.id();
            String displayName = annotation.displayName();
            String description = annotation.description();
            String category = annotation.category();
            int order = annotation.order();
            
            // 如果ID为空，则使用类名转换后的形式
            if (id.isEmpty()) {
                id = nodeClass.getSimpleName().toLowerCase();
                if (!id.endsWith("node")) {
                    id += "_node";
                }
            }
            
            // 如果显示名称为空，则使用类名的格式化版本
            if (displayName.isEmpty()) {
                displayName = formatClassName(nodeClass.getSimpleName());
            }
            
            // 检查分类是否有效
            if (category.isEmpty()) {
                NodeCraft.LOGGER.warn("节点 {} 未指定分类", nodeClass.getName());
                return false;
            }
            
            // 注册分类（如果需要）
            
            // 注册节点
            @SuppressWarnings("unchecked")
            Class<? extends INode> castedClass = (Class<? extends INode>) nodeClass;
            com.nodecraft.gui.node.NodeInfo nodeInfo = new com.nodecraft.gui.node.NodeInfo(id, displayName, description, category, order, castedClass);
            boolean success = registry.registerNode(nodeInfo);
            
            if (!success) {
                NodeCraft.LOGGER.warn("自动注册节点失败: {} ({})", displayName, id);
            }
            
            return success;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("注册节点 {} 时出错: {}", nodeClass.getName(), e.getMessage());
            return false;
        }
    }
    
    /**
     * 根据命名约定注册节点
     */
    private static boolean registerNodeByConvention(NodeRegistry registry, Class<?> nodeClass) {
        try {
            String className = nodeClass.getSimpleName();
            String packageName = nodeClass.getPackage().getName();
            
            // 从包名提取分类
            String category = extractCategoryFromPackage(packageName);
            if (category == null) {
                NodeCraft.LOGGER.warn("无法从包 {} 提取分类信息", packageName);
                return false;
            }
            
            // 设置基本信息
            String id = className.toLowerCase();
            if (!id.endsWith("node")) {
                id += "_node";
            }
            
            String displayName = formatClassName(className);
            
            // 注册分类（如果需要）
            
            // 注册节点
            @SuppressWarnings("unchecked")
            Class<? extends INode> castedClass = (Class<? extends INode>) nodeClass;
            com.nodecraft.gui.node.NodeInfo nodeInfo = new com.nodecraft.gui.node.NodeInfo(id, displayName, "", category, castedClass);

            // 成功注册的节点不再记录日志，减少输出
            
            return registry.registerNode(nodeInfo);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("按约定注册节点 {} 时出错: {}", nodeClass.getName(), e.getMessage());
            return false;
        }
    }
    
    /**
     * 从包名提取分类
     */
    private static String extractCategoryFromPackage(String packageName) {
        if (!packageName.startsWith(BASE_PACKAGE)) {
            return null;
        }
        
        // 移除基础包名前缀
        String subPackage = packageName.substring(BASE_PACKAGE.length());
        if (subPackage.startsWith(".")) {
            subPackage = subPackage.substring(1);
        }
        
        // 分割子包路径
        String[] parts = subPackage.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        
        // 构建分类ID：主分类.子分类
        return parts[0] + "." + parts[1];
    }
    
    /**
     * 确保分类存在
     */
    private static void ensureCategoryExists(NodeRegistry registry, String categoryId) {
        // 检查主分类
        if (categoryId.contains(".")) {
            String mainCategory = categoryId.substring(0, categoryId.indexOf('.'));
            if (registry.getCategory(mainCategory) == null) {
                // 注册主分类
                registry.registerCategory(new NodeRegistry.NodeCategory(
                        mainCategory, 
                        formatCategoryName(mainCategory)));
            }
        }
        
        // 检查子分类
        if (registry.getCategory(categoryId) == null) {
            // 提取显示名称
            String displayName = categoryId;
            if (categoryId.contains(".")) {
                displayName = categoryId.substring(categoryId.lastIndexOf('.') + 1);
            }
            
            // 注册子分类
            registry.registerCategory(new NodeRegistry.NodeCategory(
                    categoryId, 
                    formatCategoryName(displayName)));
        }
    }
    
    /**
     * 格式化类名为显示名称
     */
    private static String formatClassName(String className) {
        // 移除"Node"后缀
        if (className.endsWith("Node")) {
            className = className.substring(0, className.length() - 4);
        }
        
        // 在大写字母前添加空格
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
    
    /**
     * 格式化分类名称
     */
    private static String formatCategoryName(String categoryName) {
        // 将首字母大写，其他字母小写
        if (categoryName == null || categoryName.isEmpty()) {
            return categoryName;
        }
        
        return categoryName.substring(0, 1).toUpperCase() + 
               categoryName.substring(1).toLowerCase();
    }
} 
