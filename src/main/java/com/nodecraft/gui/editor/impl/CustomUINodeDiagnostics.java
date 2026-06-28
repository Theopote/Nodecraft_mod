package com.nodecraft.gui.editor.impl;

import com.nodecraft.core.NodeCraft;

/**
 * 自定义UI节点诊断工具类
 * 
 * 提供各种诊断和分析方法，帮助开发者识别自定义UI节点的潜在问题和优化机会。
 * 这些方法主要用于开发和调试阶段，不是核心功能的一部分。
 * 
 * ### 使用场景
 * - 开发阶段的问题诊断
 * - 性能优化分析
 * - 最佳实践检查
 * - 代码审查辅助
 * 
 * ### 使用示例
 * ```java
 * // 检查节点是否适合直接绘制
 * if (CustomUINodeDiagnostics.mayBenefitFromDirectDrawing(myNode)) {
 *     NodeCraft.LOGGER.info("Consider enabling direct drawing for better performance");
 * }
 * 
 * // 检查是否需要自定义内容边界
 * if (CustomUINodeDiagnostics.mayNeedCustomContentBounds(myNode)) {
 *     NodeCraft.LOGGER.info("Consider overriding getContentBounds() method");
 * }
 * 
 * // 检查ImGui ID碰撞
 * if (CustomUINodeDiagnostics.mayHaveImGuiIdCollision(nodeA, nodeB)) {
 *     NodeCraft.LOGGER.info("Potential ImGui ID collision detected");
 * }
 * ```
 */
public class CustomUINodeDiagnostics {

    /**
     * 检查节点是否可能受益于直接绘制模式。
     * 
     * ### 检查标准
     * - 节点是否已启用直接绘制
     * - 是否有复杂的自定义图形需求
     * - 是否需要性能优化
     * 
     * ### 适用场景
     * - 自定义图形绘制（图表、曲线、几何图形）
     * - 性能优化（避免ImGui控件开销）
     * - 特殊视觉效果（渐变、阴影、动画）
     * - 游戏UI元素（血条、小地图、技能图标）
     * - 数据可视化（实时图表、波形显示）
     * 
     * @param node 要检查的节点
     * @return 如果可能受益于直接绘制返回true
     */
    public static boolean mayBenefitFromDirectDrawing(BaseCustomUINode node) {
        if (node == null) {
            return false;
        }
        
        boolean supportsDirectDraw = node.supportsDirectDrawing();
        
        if (!supportsDirectDraw && isDirectDrawDebugEnabled()) {
            NodeCraft.LOGGER.info("[Direct Draw Info] Node {}: Not using direct drawing. Consider enabling if you need:", node.getId());
            NodeCraft.LOGGER.info("  - Custom graphics (charts, diagrams, etc.)");
            NodeCraft.LOGGER.info("  - Performance optimization for complex UI");
            NodeCraft.LOGGER.info("  - Special visual effects (gradients, animations)");
            NodeCraft.LOGGER.info("  - Game-specific UI elements");
        }
        
        return supportsDirectDraw;
    }

    /**
     * 检查节点是否可能需要自定义内容边界。
     * 
     * ### 检查标准
     * - 是否使用了默认的内容边界实现
     * - 是否有可能溢出的UI元素
     * - 是否设置了安全边距
     * 
     * ### 需要自定义边界的场景
     * - 阴影效果
     * - 边框装饰
     * - 下拉菜单
     * - 工具提示
     * - 弹出窗口
     * 
     * @param node 要检查的节点
     * @return 如果可能需要自定义边界返回true
     */
    public static boolean mayNeedCustomContentBounds(BaseCustomUINode node) {
        if (node == null) {
            return false;
        }
        
        // 检查是否使用了默认实现，且没有安全边距
        ICustomUINode.ContentBounds bounds = node.getContentBounds(1.0f);
        boolean hasMargins = bounds.marginLeft > 0 || bounds.marginTop > 0 || 
                           bounds.marginRight > 0 || bounds.marginBottom > 0;
        
        if (!hasMargins && isBoundsDebugEnabled()) {
            NodeCraft.LOGGER.warn("[Bounds Warning] Node {}: Using default content bounds with no margins. " +
                                "Consider overriding getContentBounds() if UI content may overflow.", node.getId());
            NodeCraft.LOGGER.info("  Common scenarios requiring custom bounds:");
            NodeCraft.LOGGER.info("  - Shadow effects");
            NodeCraft.LOGGER.info("  - Border decorations");
            NodeCraft.LOGGER.info("  - Dropdown menus");
            NodeCraft.LOGGER.info("  - Tooltips");
            NodeCraft.LOGGER.info("  - Popup windows");
        }
        
        return !hasMargins;
    }

    /**
     * 检查两个节点的ImGui ID是否可能发生碰撞。
     * 
     * ### 检查内容
     * - UUID是否相同（理论上不应该发生）
     * - hashCode是否相同（可能发生碰撞）
     * - UUID最低有效位是否相同
     * 
     * ### 碰撞影响
     * - ImGui控件状态混乱
     * - 用户输入被错误路由
     * - 界面行为异常
     * 
     * @param nodeA 第一个节点
     * @param nodeB 第二个节点
     * @return 如果可能发生ID碰撞返回true
     */
    public static boolean mayHaveImGuiIdCollision(BaseCustomUINode nodeA, BaseCustomUINode nodeB) {
        if (nodeA == null || nodeB == null || nodeA == nodeB) {
            return false;
        }
        
        // 检查UUID是否相同（这种情况理论上不应该发生）
        if (nodeA.getId().equals(nodeB.getId())) {
            if (isImGuiIdDebugEnabled()) {
                NodeCraft.LOGGER.error("[ImGui ID Error] Identical UUIDs detected between nodes {} and {}", 
                                     nodeA.getId(), nodeB.getId());
            }
            return true;
        }
        
        // 检查hashCode是否相同（可能发生碰撞）
        if (nodeA.getId().hashCode() == nodeB.getId().hashCode()) {
            if (isImGuiIdDebugEnabled()) {
                NodeCraft.LOGGER.warn("[ImGui ID Warning] Potential hashCode collision detected between nodes {} and {}", 
                                    nodeA.getId(), nodeB.getId());
            }
            return true;
        }
        
        // 检查最低有效位是否相同
        if (nodeA.getId().getLeastSignificantBits() == nodeB.getId().getLeastSignificantBits()) {
            if (isImGuiIdDebugEnabled()) {
                NodeCraft.LOGGER.warn("[ImGui ID Warning] Potential LSB collision detected between nodes {} and {}", 
                                    nodeA.getId(), nodeB.getId());
            }
            return true;
        }
        
        return false;
    }

    /**
     * 对节点进行全面的诊断检查。
     * 
     * 这是一个便利方法，会运行所有可用的诊断检查并生成报告。
     * 
     * @param node 要诊断的节点
     * @return 诊断报告
     */
    public static DiagnosticReport runFullDiagnostic(BaseCustomUINode node) {
        if (node == null) {
            return new DiagnosticReport("Node is null");
        }
        
        DiagnosticReport report = new DiagnosticReport(node.getId().toString());
        
        // 检查直接绘制
        boolean benefitsFromDirectDraw = mayBenefitFromDirectDrawing(node);
        report.addFinding("Direct Drawing", benefitsFromDirectDraw ? "Enabled" : "Consider enabling", 
                         benefitsFromDirectDraw ? DiagnosticReport.Severity.INFO : DiagnosticReport.Severity.SUGGESTION);
        
        // 检查内容边界
        boolean needsCustomBounds = mayNeedCustomContentBounds(node);
        report.addFinding("Content Bounds", needsCustomBounds ? "May need customization" : "OK", 
                         needsCustomBounds ? DiagnosticReport.Severity.WARNING : DiagnosticReport.Severity.INFO);
        
        // 检查缓存状态
        boolean cacheValid = node.isCacheValid();
        report.addFinding("Cache Status", cacheValid ? "Valid" : "Invalid", 
                         cacheValid ? DiagnosticReport.Severity.INFO : DiagnosticReport.Severity.WARNING);
        
        // 检查调试模式
        boolean debugEnabled = node.isDebugMode();
        if (debugEnabled && node.isDebugEnabledInProduction()) {
            report.addFinding("Debug Mode", "Enabled in production", DiagnosticReport.Severity.ERROR);
        } else if (debugEnabled) {
            report.addFinding("Debug Mode", "Enabled", DiagnosticReport.Severity.INFO);
        }
        
        return report;
    }

    /**
     * 诊断报告类
     */
    public static class DiagnosticReport {
        public enum Severity {
            INFO, SUGGESTION, WARNING, ERROR
        }
        
        public static class Finding {
            public final String category;
            public final String message;
            public final Severity severity;
            
            public Finding(String category, String message, Severity severity) {
                this.category = category;
                this.message = message;
                this.severity = severity;
            }
            
            @Override
            public String toString() {
                return String.format("[%s] %s: %s", severity, category, message);
            }
        }
        
        private final String nodeId;
        private final java.util.List<Finding> findings = new java.util.ArrayList<>();
        
        public DiagnosticReport(String nodeId) {
            this.nodeId = nodeId;
        }
        
        public void addFinding(String category, String message, Severity severity) {
            findings.add(new Finding(category, message, severity));
        }
        
        public java.util.List<Finding> getFindings() {
            return new java.util.ArrayList<>(findings);
        }
        
        public boolean hasErrors() {
            return findings.stream().anyMatch(f -> f.severity == Severity.ERROR);
        }
        
        public boolean hasWarnings() {
            return findings.stream().anyMatch(f -> f.severity == Severity.WARNING);
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Diagnostic Report for Node: ").append(nodeId).append("\n");
            for (Finding finding : findings) {
                sb.append("  ").append(finding).append("\n");
            }
            return sb.toString();
        }
    }

    // === 调试状态检查方法 ===
    
    private static boolean isDirectDrawDebugEnabled() {
        return DebugManager.getInstance().isDirectDrawDebugEnabled();
    }
    
    private static boolean isBoundsDebugEnabled() {
        return DebugManager.getInstance().isBoundsDebugEnabled();
    }
    
    private static boolean isImGuiIdDebugEnabled() {
        return DebugManager.getInstance().isImGuiIdDebugEnabled();
    }
}
