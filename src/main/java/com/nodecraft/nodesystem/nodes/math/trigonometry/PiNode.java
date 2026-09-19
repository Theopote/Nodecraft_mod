package com.nodecraft.nodesystem.nodes.math.trigonometry;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Pi Node: Outputs the constant value of Pi.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.trigonometry.pi",
    displayName = "Pi",
    description = "输出数学常数π的值",
    category = "math.trigonometry",
    order = 8
)
public class PiNode extends BaseNode {

    // --- 输入端口 (无) ---

    // --- 输出端口 IDs ---
    private static final String OUTPUT_PI_ID = "output_pi";

    // --- 构造函数 ---
    public PiNode() {
        super(UUID.randomUUID(), "math.trigonometry.pi");
        
        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_PI_ID, "Pi", "The value of Pi", NodeDataType.DOUBLE, this));
        
        // 在构造函数中直接设置输出值，因为它永远不变
        outputValues.put(OUTPUT_PI_ID, Math.PI);
    }

    // --- 核心逻辑 ---
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 常量节点，processNode 为空，值在构造函数中设置
        // 确保值仍然存在 (虽然不太可能被清除)
        if (!outputValues.containsKey(OUTPUT_PI_ID)) {
             outputValues.put(OUTPUT_PI_ID, Math.PI);
        }
    }

    // --- Getters/Setters (不需要) ---

    // --- (反)序列化 (不需要) ---

    @Override
    public String getDescription() {
        return "Outputs the mathematical constant Pi.";
    }

    @Override
    public String getDisplayName() {
        return "Pi (π)";
    }
} 
