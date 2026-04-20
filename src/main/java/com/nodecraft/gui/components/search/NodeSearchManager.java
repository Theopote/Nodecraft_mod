package com.nodecraft.gui.components.search;

import java.util.function.Consumer;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.registry.NodeRegistry.NodeCategory;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImString;

/**
 * 节点搜索管理器
 * 专注于处理节点搜索功能，包括搜索词处理和文本高亮显示
 */
public class NodeSearchManager {
    // 搜索相关常量
    public static final int SEARCH_QUERY_BUFFER_SIZE = 256;
    public static final String SEARCH_HINT_TEXT = "搜索节点...";
    
    // 搜索状态
    private final ImString searchQuery;
    private String lastSearchTerm = "";
    
    /**
     * 构造函数
     */
    public NodeSearchManager() {
        this.searchQuery = new ImString("", SEARCH_QUERY_BUFFER_SIZE);
    }
    
    /**
     * 渲染搜索栏
     * @param onSearchChanged 搜索变化时的回调
     * @return 搜索是否发生变化
     */
    public boolean renderSearchBar(Consumer<String> onSearchChanged) {
        ImGui.pushItemWidth(-1); // 使搜索栏填满宽度
        
        // 设置搜索框样式
        ImGui.pushStyleColor(ImGuiCol.FrameBg, ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1.0f));
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, ImGui.colorConvertFloat4ToU32(0.25f, 0.25f, 0.25f, 1.0f));
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, 1.0f));
        
        // 保存搜索框内容的副本，用于检测变化
        String oldSearchContent = this.searchQuery.get();
        
        // 检查搜索输入框的返回值，以处理输入事件
        boolean searchChanged = ImGui.inputTextWithHint("##searchNodes", SEARCH_HINT_TEXT, searchQuery);
        
        // 恢复样式
        ImGui.popStyleColor(3);
        ImGui.popItemWidth();
        
        // 检查搜索内容是否发生变化
        String newSearchContent = this.searchQuery.get();
        boolean contentChanged = !oldSearchContent.equals(newSearchContent);
        
        // 调试日志
        if (contentChanged) {
            NodeCraft.LOGGER.info("搜索输入框内容已更改: '{}' -> '{}'", oldSearchContent, newSearchContent);
            searchChanged = true;
        } else if (searchChanged) {
            NodeCraft.LOGGER.info("搜索框状态变化，但内容未改变: '{}'", newSearchContent);
        }
        
        // 检查搜索词是否发生变化
        String currentSearchTerm = searchQuery.get().toLowerCase();
        boolean termChanged = !currentSearchTerm.equals(lastSearchTerm);
        
        if (termChanged || searchChanged) {
            lastSearchTerm = currentSearchTerm;
            
            // 调用回调
            if (onSearchChanged != null) {
                NodeCraft.LOGGER.info("调用搜索变化回调，搜索词: '{}'", currentSearchTerm);
                onSearchChanged.accept(currentSearchTerm);
            } else {
                NodeCraft.LOGGER.warn("搜索变化回调为空，无法通知变化");
            }
            
            // 记录日志
            NodeCraft.LOGGER.info("搜索条件更新: {}", currentSearchTerm.isEmpty() ? "<空>" : currentSearchTerm);
            
            return true; // 搜索发生变化
        }
        
        return false; // 搜索未变化
    }
    
    /**
     * 搜索节点
     * @param node 要搜索的节点
     * @param searchTerm 搜索关键词
     * @return 是否匹配
     */
    public boolean matchesNode(NodeInfo node, String searchTerm) {
        if (searchTerm == null || searchTerm.isEmpty()) {
            return true; // 空搜索词匹配所有节点
        }
        
        try {
            // 直接使用单个搜索词，转为小写进行比较
            String processedTerm = searchTerm.toLowerCase().trim();
            
            // 获取节点的所有文本属性
            String displayName = node.getDisplayName() != null ? node.getDisplayName().toLowerCase() : "";
            String nodeId = node.getId() != null ? node.getId().toLowerCase() : "";
            String description = node.getDescription() != null ? node.getDescription().toLowerCase() : "";
            
            // 简单的字符串包含检查
            boolean nameMatch = displayName.contains(processedTerm);
            boolean idMatch = nodeId.contains(processedTerm);
            boolean descMatch = description.contains(processedTerm);
            boolean matches = nameMatch || idMatch || descMatch;
            
            if (matches) {
                // 详细记录匹配的原因
                StringBuilder reason = new StringBuilder("节点匹配");
                if (nameMatch) reason.append(", 名称包含搜索词");
                if (idMatch) reason.append(", ID包含搜索词");
                if (descMatch) reason.append(", 描述包含搜索词");
                
                NodeCraft.LOGGER.debug("{}: {} ({})", reason, node.getDisplayName(), node.getId());
            }
            
            return matches;
        } catch (Exception e) {
            // 捕获任何异常，避免搜索崩溃
            NodeCraft.LOGGER.error("搜索节点时出错: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 搜索分类
     * @param category 要搜索的分类
     * @param searchTerm 搜索关键词
     * @return 是否匹配
     */
    public boolean matchesCategory(NodeCategory category, String searchTerm) {
        if (searchTerm == null || searchTerm.isEmpty()) {
            return true; // 空搜索词匹配所有分类
        }
        
        try {
            // 直接使用单个搜索词，转为小写进行比较
            String processedTerm = searchTerm.toLowerCase().trim();
            
            // 提取需要匹配的文本
            String displayName = category.getDisplayName().toLowerCase();
            String categoryId = category.getId().toLowerCase();
            
            // 简单的字符串包含检查

            return displayName.contains(processedTerm) || categoryId.contains(processedTerm);
        } catch (Exception e) {
            // 捕获任何异常
            NodeCraft.LOGGER.error("搜索分类时出错: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 在搜索模式下突出显示节点文本中的匹配部分
     * @param drawList ImGui绘图列表
     * @param text 要显示的文本
     * @param x 文本X坐标
     * @param y 文本Y坐标
     * @param searchTerm 搜索词
     * @param defaultColor 默认文本颜色
     * @param highlightColor 高亮文本颜色
     */
    public void renderHighlightedText(ImDrawList drawList, String text, float x, float y, 
                                     String searchTerm, int defaultColor, int highlightColor) {
        if (text == null || text.isEmpty() || searchTerm == null || searchTerm.isEmpty()) {
            // 如果没有搜索词或文本为空，直接渲染普通文本
            drawList.addText(x, y, defaultColor, text);
            return;
        }
        
        // 转换为小写以进行不区分大小写的匹配
        String lowerText = text.toLowerCase();
        String lowerSearchTerm = searchTerm.toLowerCase();
        
        // 如果没有匹配，直接渲染普通文本
        if (!lowerText.contains(lowerSearchTerm)) {
            drawList.addText(x, y, defaultColor, text);
            return;
        }
        
        // 简单的单个匹配处理
        int matchPos = lowerText.indexOf(lowerSearchTerm);
        int matchEnd = matchPos + lowerSearchTerm.length();
        
        float currentX = x;
        
        // 渲染前导非匹配部分
        if (matchPos > 0) {
            String prefix = text.substring(0, matchPos);
            drawList.addText(currentX, y, defaultColor, prefix);
            currentX += ImGui.calcTextSize(prefix).x;
        }
        
        // 渲染匹配部分
        String match = text.substring(matchPos, matchEnd);
        drawList.addText(currentX, y, highlightColor, match);
        currentX += ImGui.calcTextSize(match).x;
        
        // 渲染末尾非匹配部分
        if (matchEnd < text.length()) {
            String suffix = text.substring(matchEnd);
            drawList.addText(currentX, y, defaultColor, suffix);
        }
    }
    
    /**
     * 渲染搜索未找到结果时的提示
     */
    public void renderNoMatchesMessage() {
        if (!lastSearchTerm.isEmpty()) {
            ImGui.text("  没有找到匹配的节点");
            
            // 显示更详细的搜索建议
            ImGui.spacing();
            ImGui.text("  搜索提示:");
            ImGui.bullet(); ImGui.text("尝试使用不同的关键词");
            ImGui.bullet(); ImGui.text("使用节点名称的一部分");
            ImGui.bullet(); ImGui.text("查看分类名称");
            
            // 如果搜索词较长，建议使用短词
            if (lastSearchTerm.length() > 3) {
                ImGui.spacing();
                ImGui.text("  搜索词可能过于具体，尝试使用更短的词");
            }
        }
    }
    
    /**
     * 获取当前搜索词
     * @return 当前搜索词
     */
    public String getSearchTerm() {
        return lastSearchTerm;
    }
    
    /**
     * 清空搜索
     */
    public void clearSearch() {
        searchQuery.set("");
        lastSearchTerm = "";
    }
} 