package com.nodecraft.nodesystem.nodes.pattern.lsystem;

import com.nodecraft.gui.components.customui.CustomUIMetricsSafe;
import com.nodecraft.gui.components.customui.CustomUIPropertyHistory;
import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LSystemRule;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import imgui.ImGui;
import imgui.type.ImFloat;
import imgui.type.ImString;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Constructs one L-system production rule.
 *
 * <p><b>Input contract (Phase 2 custom UI):</b>
 * <ul>
 *   <li>An undriven Symbol / Production port uses the local draft (new nodes ship a valid default).</li>
 *   <li>A driven (connected) Symbol or Production port takes precedence over the draft.</li>
 *   <li>Invalid connected input fails closed — never silently falls back to the draft.</li>
 *   <li>Empty Production ({@code ""}) is valid; blank Symbol is not.</li>
 *   <li>Weight must be finite and {@code >= 0}.</li>
 * </ul>
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.lsystem.rule",
    displayName = "L-System Rule",
    description = "Constructs one L-system production rule from symbol, production string, and relative weight",
    category = "pattern.lsystem",
    order = 0
)
public class LSystemRuleNode extends BaseCustomUINode {

    @NodeProperty(displayName = "Weight", category = "Rule", order = 1,
        description = "Relative weight when multiple rules share the same symbol")
    private double weight = 1.0d;

    /** Local draft used when Symbol port is undriven. */
    private String draftSymbol = "F";
    /** Local draft used when Production port is undriven; empty string is valid. */
    private String draftProduction = "F[+F]F[-F]F";

    private static final String INPUT_SYMBOL_ID = "input_symbol";
    private static final String INPUT_PRODUCTION_ID = "input_production";
    private static final String INPUT_WEIGHT_ID = "input_weight";

    private static final String OUTPUT_RULE_ID = "output_rule";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    private transient ImString symbolBuffer;
    private transient ImString productionBuffer;
    /** Undo baseline captured when a text/weight field becomes active. */
    private transient Object fieldEditBeforeState;
    private transient Double weightEditBefore;

    public LSystemRuleNode() {
        super(UUID.randomUUID(), "pattern.lsystem.rule");

        addInputPort(new BasePort(INPUT_SYMBOL_ID, "Symbol", "Symbol to rewrite (whitespace is significant; only all-whitespace is invalid)", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_PRODUCTION_ID, "Production", "Replacement string (may be empty)", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_WEIGHT_ID, "Weight", "Relative weight for stochastic choice", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_RULE_ID, "Rule", "Constructed L-system rule", NodeDataType.L_SYSTEM_RULE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the rule inputs are valid", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Constructs one L-system production rule from symbol, production string, and relative weight";
    }

    @Override
    public String getDisplayName() {
        return "L-System Rule";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String symbol;
        if (OptionalPortDrive.isDriven(this, INPUT_SYMBOL_ID)) {
            Object symbolRaw = inputValues.get(INPUT_SYMBOL_ID);
            if (!(symbolRaw instanceof String text) || text.trim().isEmpty()) {
                writeInvalid("Symbol is required");
                return;
            }
            symbol = text;
        } else {
            if (draftSymbol == null || draftSymbol.trim().isEmpty()) {
                writeInvalid("Symbol is required");
                return;
            }
            symbol = draftSymbol;
        }

        String production;
        if (OptionalPortDrive.isDriven(this, INPUT_PRODUCTION_ID)) {
            Object productionRaw = inputValues.get(INPUT_PRODUCTION_ID);
            if (!(productionRaw instanceof String text)) {
                writeInvalid("Production must be a String");
                return;
            }
            production = text;
        } else {
            production = draftProduction == null ? "" : draftProduction;
        }

        Double ruleWeight = OptionalPortDrive.resolveOptionalDouble(this, INPUT_WEIGHT_ID, weight);
        if (ruleWeight == null) {
            writeInvalid(OptionalPortDrive.isConnected(this, INPUT_WEIGHT_ID)
                ? "Weight connected but invalid"
                : "Weight must be finite and >= 0");
            return;
        }
        if (ruleWeight < 0.0d) {
            writeInvalid("Weight must be finite and >= 0");
            return;
        }

        outputValues.put(OUTPUT_RULE_ID, new LSystemRule(symbol, production, ruleWeight));
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    public String getDraftSymbol() {
        return draftSymbol == null ? "" : draftSymbol;
    }

    public void setDraftSymbol(String value) {
        draftSymbol = value == null ? "" : value;
        markDirty();
    }

    public String getDraftProduction() {
        return draftProduction == null ? "" : draftProduction;
    }

    public void setDraftProduction(String value) {
        draftProduction = value == null ? "" : value;
        markDirty();
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double value) {
        // Allow non-finite values so setNodeState can fail-closed via resolveOptionalDouble.
        if (Double.compare(weight, value) != 0) {
            weight = value;
            markDirty();
        }
    }

    /** Package-visible for edit-transaction tests. */
    Object pendingFieldEditBaseline() {
        return fieldEditBeforeState;
    }

    /**
     * Applies a continuous draft edit through the same activate/preview/commit path as the custom UI.
     */
    public void applyDraftTextEditTransaction(String newSymbol, String newProduction) {
        beginFieldEdit();
        setDraftSymbol(newSymbol);
        setDraftProduction(newProduction);
        commitFieldEdit("Edit L-System rule");
    }

    private void beginFieldEdit() {
        if (fieldEditBeforeState == null) {
            fieldEditBeforeState = getNodeState();
        }
    }

    private void commitFieldEdit(String label) {
        Object before = fieldEditBeforeState;
        if (before == null) {
            return;
        }
        Object after = getNodeState();
        fieldEditBeforeState = null;
        if (!Objects.equals(before, after)) {
            CustomUIPropertyHistory.recordNodeStateChange(getId(), before, after, label);
        }
    }

    private void beginWeightEdit() {
        if (weightEditBefore == null) {
            weightEditBefore = weight;
        }
    }

    private void commitWeightEdit() {
        Double before = weightEditBefore;
        if (before == null) {
            return;
        }
        weightEditBefore = null;
        if (Double.compare(before, weight) != 0) {
            CustomUIPropertyHistory.recordPropertyChange(getId(), "weight", before, weight, 0L);
        }
    }

    @Override
    protected float calculateUIHeight() {
        float frame = CustomUIMetricsSafe.layoutFrameHeight();
        float line = CustomUIMetricsSafe.layoutTextLineHeight();
        float gap = CustomUIMetricsSafe.layoutItemSpacingY();
        // symbol→production row + weight row + Valid label
        return frame + gap + frame + gap + line + getSmallPadding();
    }

    @Override
    protected float calculateMinUIWidth() {
        return 168.0f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            float edgeMargin = l.toPixels(getSmallPadding());
            float availableWidth = Math.max(96.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
            ImGui.setCursorPosX(ImGui.getCursorPosX() + edgeMargin);

            boolean symbolDriven = OptionalPortDrive.isDriven(this, INPUT_SYMBOL_ID);
            boolean productionDriven = OptionalPortDrive.isDriven(this, INPUT_PRODUCTION_ID);
            boolean weightDriven = OptionalPortDrive.isDriven(this, INPUT_WEIGHT_ID);
            boolean changed = false;

            float half = Math.max(40.0f, availableWidth * 0.28f);

            // Symbol — independent of Production drive state
            if (symbolDriven) {
                ImGui.textDisabled(truncate(resolveDisplaySymbol(), 16));
            } else {
                if (symbolBuffer == null) {
                    symbolBuffer = new ImString(getDraftSymbol(), 64);
                }
                if (!ImGui.isAnyItemActive() && !getDraftSymbol().equals(symbolBuffer.get())) {
                    symbolBuffer.set(getDraftSymbol());
                }
                ImGui.setNextItemWidth(half);
                ImGui.inputText("##lsys_symbol", symbolBuffer);
                if (ImGui.isItemActivated()) {
                    beginFieldEdit();
                }
                if (ImGui.isItemActive() && !getDraftSymbol().equals(symbolBuffer.get())) {
                    setDraftSymbol(symbolBuffer.get());
                    changed = true;
                }
                if (ImGui.isItemDeactivatedAfterEdit()) {
                    if (!getDraftSymbol().equals(symbolBuffer.get())) {
                        setDraftSymbol(symbolBuffer.get());
                    }
                    commitFieldEdit("Edit L-System symbol");
                    changed = true;
                }
            }

            ImGui.sameLine();
            ImGui.textDisabled("→");
            ImGui.sameLine();

            // Production — independent of Symbol drive state
            if (productionDriven) {
                ImGui.textDisabled(truncate(resolveDisplayProduction(), 24));
            } else {
                if (productionBuffer == null) {
                    productionBuffer = new ImString(getDraftProduction(), 256);
                }
                if (!ImGui.isAnyItemActive() && !getDraftProduction().equals(productionBuffer.get())) {
                    productionBuffer.set(getDraftProduction());
                }
                ImGui.setNextItemWidth(Math.max(48.0f, availableWidth - half - 28.0f * zoom));
                ImGui.inputText("##lsys_production", productionBuffer);
                if (ImGui.isItemActivated()) {
                    beginFieldEdit();
                }
                if (ImGui.isItemActive() && !getDraftProduction().equals(productionBuffer.get())) {
                    setDraftProduction(productionBuffer.get());
                    changed = true;
                }
                if (ImGui.isItemDeactivatedAfterEdit()) {
                    if (!getDraftProduction().equals(productionBuffer.get())) {
                        setDraftProduction(productionBuffer.get());
                    }
                    commitFieldEdit("Edit L-System production");
                    changed = true;
                }
            }

            Double ruleWeight = OptionalPortDrive.resolveOptionalDouble(this, INPUT_WEIGHT_ID, weight);
            double shownWeight = ruleWeight != null ? ruleWeight : weight;
            if (weightDriven) {
                ImGui.textDisabled(String.format("Weight: %.2f", shownWeight));
            } else {
                ImFloat weightEdit = new ImFloat((float) weight);
                ImGui.setNextItemWidth(Math.min(availableWidth * 0.5f, l.toPixels(96.0f)));
                ImGui.inputFloat("Weight##lsys_weight", weightEdit, 0.0f, 0.0f, "%.2f");
                if (ImGui.isItemActivated()) {
                    beginWeightEdit();
                }
                if (ImGui.isItemActive()) {
                    float live = weightEdit.get();
                    if (Float.isFinite(live) && Math.abs(live - weight) > 1.0e-6d) {
                        setWeight(live);
                        changed = true;
                    }
                }
                if (ImGui.isItemDeactivatedAfterEdit()) {
                    float live = weightEdit.get();
                    if (Float.isFinite(live)) {
                        setWeight(live);
                    }
                    commitWeightEdit();
                    changed = true;
                }
            }

            if (Boolean.TRUE.equals(outputValues.get(OUTPUT_VALID_ID))) {
                ImGui.textColored(0.35f, 0.75f, 0.4f, 1.0f, "Valid");
            } else {
                Object err = outputValues.get(OUTPUT_ERROR_ID);
                ImGui.textColored(0.9f, 0.35f, 0.3f, 1.0f,
                    err instanceof String s && !s.isBlank() ? s : "Invalid");
            }
            return changed;
        });
    }

    private String resolveDisplaySymbol() {
        if (OptionalPortDrive.isDriven(this, INPUT_SYMBOL_ID)
            && inputValues.get(INPUT_SYMBOL_ID) instanceof String text) {
            return text;
        }
        return getDraftSymbol();
    }

    private String resolveDisplayProduction() {
        if (OptionalPortDrive.isDriven(this, INPUT_PRODUCTION_ID)
            && inputValues.get(INPUT_PRODUCTION_ID) instanceof String text) {
            return text;
        }
        return getDraftProduction();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, Math.max(1, max - 1)) + "…";
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_RULE_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("weight", weight);
        state.put("draftSymbol", getDraftSymbol());
        state.put("draftProduction", getDraftProduction());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("weight") instanceof Number number) {
            setWeight(number.doubleValue());
        }
        if (map.get("draftSymbol") instanceof String text) {
            setDraftSymbol(text);
        }
        if (map.get("draftProduction") instanceof String text) {
            setDraftProduction(text);
        }
    }
}
