package com.nodecraft.nodesystem.nodes.utilities.assist;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 中继节点（Reroute）：用于缩短长连线、优化画布走线。
 * 节点只做透传，不引入任何业务逻辑。
 */
@NodeInfo(
    id = "utilities.assist.reroute",
    displayName = "Reroute",
    description = "用于整理连线的中继节点，仅透传输入到输出",
    category = "utilities.assist"
)
public class RerouteNode extends BaseNode {

    private static final String INPUT_SIGNAL_ID = "input_signal";
    private static final String OUTPUT_SIGNAL_ID = "output_signal";

    public RerouteNode() {
        super(UUID.randomUUID(), "utilities.assist.reroute");

        addInputPort(new BasePort(
            INPUT_SIGNAL_ID,
            "输入",
            "需要透传的信号",
            NodeDataType.ANY,
            this
        ));

        addOutputPort(new BasePort(
            OUTPUT_SIGNAL_ID,
            "输出",
            "透传后的信号",
            NodeDataType.ANY,
            this
        ));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        outputValues.put(OUTPUT_SIGNAL_ID, inputValues.get(INPUT_SIGNAL_ID));
    }
}
