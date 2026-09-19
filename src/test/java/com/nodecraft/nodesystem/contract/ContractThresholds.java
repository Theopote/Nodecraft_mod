package com.nodecraft.nodesystem.contract;

/**
 * Catalog contract soft ceilings by release phase.
 * <p>
 * Roadmap (unexpected failures on {@link com.nodecraft.nodesystem.api.ContractTestEnvironment#UNIT} nodes):
 * <ul>
 *   <li>0.8 — &lt; 25%</li>
 *   <li>0.9-A — &lt; 10%</li>
 *   <li>0.9-B — &lt; 3%</li>
 *   <li>1.0 — 0 unexpected failures</li>
 * </ul>
 */
public final class ContractThresholds {

    public static final String PHASE = "0.9-A";

    /** Max fraction of UNIT-eligible catalog entries missing {@code @NodeInfo}. */
    public static final double MAX_MISSING_NODE_INFO_RATIO = 0.0;

    /** Max fraction of UNIT-eligible nodes that fail {@code createNodeInstance}. */
    public static final double MAX_INSTANTIATE_FAILURE_RATIO = 0.10;

    /** Max fraction of checked instantiable UNIT nodes that fail ser/de roundtrip. */
    public static final double MAX_SERDE_FAILURE_RATIO = 0.10;

    private ContractThresholds() {
    }
}
