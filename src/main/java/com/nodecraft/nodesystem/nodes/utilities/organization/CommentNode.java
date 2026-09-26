package com.nodecraft.nodesystem.nodes.utilities.organization;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Legacy comment node — migrated to {@link com.nodecraft.nodesystem.io.SavedGraphComment} metadata in V58.
 */
@Deprecated
public class CommentNode extends BaseNode {

    // --- 节点属性 ---
    private String commentText = "在此处添加注释"; // 注释文本
    private String textColor = "#000000"; // 文本颜色
    private String backgroundColor = "#FFEB3B"; // 背景颜色
    private float fontSize = 14.0f; // 字体大小
    private boolean isBold = false; // 是否加粗
    private boolean isItalic = false; // 是否倾斜
    private CommentStyle style = CommentStyle.STICKY_NOTE; // 注释样式
    private String description = "在画布上添加文本注释";
    
    // --- 尺寸属性 ---
    private double width = 200.0;
    private double height = 120.0;
    
    /**
     * 注释样式枚举
     */
    public enum CommentStyle {
        STICKY_NOTE("便签", "类似便利贴的样式"),
        BOX("方框", "带边框的方框"),
        FREE_TEXT("自由文本", "无边框的纯文本"),
        CALLOUT("标注", "带有指向线的标注");
        
        private final String displayName;
        private final String description;
        
        CommentStyle(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 构造一个新的注释节点
     */
    public CommentNode() {
        super(UUID.randomUUID(), "utilities.organization.comment");
        
        // 注释节点没有输入输出端口
        // 纯UI节点，不参与数据流
    }
    
    @Override
    public String getDescription() {
        return this.description;
    }
    
    /**
     * 节点的计算逻辑
     * 对于Comment节点，不执行任何实际计算
     */
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 注释节点不处理任何数据
    }
    
    // --- Getters/Setters for Properties ---
    
    public String getCommentText() {
        return commentText;
    }
    
    public void setCommentText(String commentText) {
        this.commentText = commentText;
        markDirty();
    }
    
    public String getTextColor() {
        return textColor;
    }
    
    public void setTextColor(String textColor) {
        this.textColor = textColor;
        markDirty();
    }
    
    public String getBackgroundColor() {
        return backgroundColor;
    }
    
    public void setBackgroundColor(String backgroundColor) {
        this.backgroundColor = backgroundColor;
        markDirty();
    }
    
    public float getFontSize() {
        return fontSize;
    }
    
    public void setFontSize(float fontSize) {
        // 限制字体大小的范围
        this.fontSize = Math.max(8.0f, Math.min(40.0f, fontSize));
        markDirty();
    }
    
    public boolean isBold() {
        return isBold;
    }
    
    public void setBold(boolean bold) {
        this.isBold = bold;
        markDirty();
    }
    
    public boolean isItalic() {
        return isItalic;
    }
    
    public void setItalic(boolean italic) {
        this.isItalic = italic;
        markDirty();
    }
    
    public CommentStyle getStyle() {
        return style;
    }
    
    public void setStyle(CommentStyle style) {
        this.style = style;
        markDirty();
    }
    
    public double getWidth() {
        return width;
    }
    
    public void setWidth(double width) {
        this.width = Math.max(50, width); // 最小宽度为50
        markDirty();
    }
    
    public double getHeight() {
        return height;
    }
    
    public void setHeight(double height) {
        this.height = Math.max(30, height); // 最小高度为30
        markDirty();
    }
    
    /**
     * 保存节点状态
     */
    @Override
    public @Nullable Object getNodeState() {
        Object[] state = new Object[9];
        state[0] = commentText;
        state[1] = textColor;
        state[2] = backgroundColor;
        state[3] = fontSize;
        state[4] = isBold;
        state[5] = isItalic;
        state[6] = style.name();
        state[7] = width;
        state[8] = height;
        return state;
    }
    
    /**
     * 恢复节点状态
     */
    @Override
    public void setNodeState(@Nullable Object state) {
        if (state instanceof Object[]) {
            Object[] objState = (Object[]) state;
            if (objState.length >= 9) {
                if (objState[0] instanceof String) {
                    commentText = (String) objState[0];
                }
                if (objState[1] instanceof String) {
                    textColor = (String) objState[1];
                }
                if (objState[2] instanceof String) {
                    backgroundColor = (String) objState[2];
                }
                if (objState[3] instanceof Number) {
                    fontSize = ((Number) objState[3]).floatValue();
                }
                if (objState[4] instanceof Boolean) {
                    isBold = (Boolean) objState[4];
                }
                if (objState[5] instanceof Boolean) {
                    isItalic = (Boolean) objState[5];
                }
                if (objState[6] instanceof String) {
                    try {
                        style = CommentStyle.valueOf((String) objState[6]);
                    } catch (IllegalArgumentException e) {
                        style = CommentStyle.STICKY_NOTE; // 默认样式
                    }
                }
                if (objState[7] instanceof Number) {
                    width = ((Number) objState[7]).doubleValue();
                }
                if (objState[8] instanceof Number) {
                    height = ((Number) objState[8]).doubleValue();
                }
            }
        }
    }
} 