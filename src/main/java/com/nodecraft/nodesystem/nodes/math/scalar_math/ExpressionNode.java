package com.nodecraft.nodesystem.nodes.math.scalar_math;

import com.nodecraft.core.exception.ExpressionEvaluationException;
import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImString;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "math.scalar_math.expression",
    displayName = "Expression",
    description = "Evaluates a numeric expression using input variables such as A, B, C, X, Y, Z, and T",
    category = "math.scalar_math",
    order = 23
)
public class ExpressionNode extends BaseCustomUINode {

    private static final int MAX_EXPRESSION_LENGTH = 2048;
    private static final int MAX_PARSE_DEPTH = 100;

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String INPUT_C_ID = "input_c";
    private static final String INPUT_X_ID = "input_x";
    private static final String INPUT_Y_ID = "input_y";
    private static final String INPUT_Z_ID = "input_z";
    private static final String INPUT_T_ID = "input_t";

    private static final String OUTPUT_RESULT_ID = "output_result";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "Expression", category = "Formula", order = 1)
    private String expression = "A + B";

    @NodeProperty(displayName = "Show Error", category = "UI", order = 10)
    private boolean showError = true;

    private transient ImString expressionBuffer;
    private transient boolean bufferNeedsSync = true;
    private transient String lastError = "";

    public ExpressionNode() {
        super(UUID.randomUUID(), "math.scalar_math.expression");

        addInputPort(new BasePort(INPUT_A_ID, "A", "Variable A", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Variable B", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_C_ID, "C", "Variable C", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_X_ID, "X", "Variable X", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Y_ID, "Y", "Variable Y", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Z_ID, "Z", "Variable Z", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_T_ID, "T", "Variable T", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "Expression result", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the expression evaluated successfully", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Parse or evaluation error", NodeDataType.STRING, this));
        updateOutput(0.0d, true, "");
    }

    @Override
    public String getDisplayName() {
        return "Expression";
    }

    @Override
    public String getDescription() {
        return "Evaluates a numeric expression using input variables such as A, B, C, X, Y, Z, and T";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        try {
            Map<String, Double> variables = readVariables();
            double result = new Parser(expression, variables).parse();
            if (!Double.isFinite(result)) {
                throw new ExpressionEvaluationException("Result is not finite");
            }
            updateOutput(result, true, "");
        } catch (ExpressionEvaluationException e) {
            updateOutput(Double.NaN, false, e.getMessage());
        }
    }

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += ImGui.getFrameHeight();
        if (showError && lastError != null && !lastError.isBlank()) {
            height += getSmallPadding();
            height += ImGui.getTextLineHeight();
        }
        height += getSmallPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 260.0f;
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            float edgeMargin = l.toPixels(getSmallPadding());
            float availableWidth = Math.max(96.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
            float baseCursorX = ImGui.getCursorPosX();

            ensureBuffer();
            l.addVerticalSpacing(getMediumPadding());

            ImGui.setCursorPosX(baseCursorX + edgeMargin);
            l.setItemWidth(availableWidth / Math.max(zoom, 0.001f));
            if (ImGui.inputTextWithHint("##expression", "A + sin(T) * 10", expressionBuffer,
                ImGuiInputTextFlags.EnterReturnsTrue)) {
                changed = applyExpression(expressionBuffer.get());
            }
            if (ImGui.isItemDeactivatedAfterEdit()) {
                changed |= applyExpression(expressionBuffer.get());
            }
            l.popItemWidth();

            if (showError && lastError != null && !lastError.isBlank()) {
                l.addVerticalSpacing(getSmallPadding());
                String text = clipError(lastError);
                ImGui.setCursorPosX(baseCursorX + edgeMargin);
                ImGui.pushStyleColor(ImGuiCol.Text, 0.95f, 0.36f, 0.32f, 1.0f);
                ImGui.text(text);
                ImGui.popStyleColor();
            }

            l.addVerticalSpacing(getSmallPadding());
            return changed || ImGui.isItemHovered() || ImGui.isItemActive();
        });
    }

    private void ensureBuffer() {
        if (expressionBuffer == null || bufferNeedsSync) {
            expressionBuffer = new ImString(expression == null ? "" : expression, 2048);
            bufferNeedsSync = false;
        }
    }

    private boolean applyExpression(String rawExpression) {
        String next = normalizeExpression(rawExpression);
        if (!next.equals(expression)) {
            expression = next;
            bufferNeedsSync = true;
            markDirty();
            return true;
        }
        return false;
    }

    private static String normalizeExpression(String rawExpression) {
        String next = rawExpression == null ? "" : rawExpression.trim();
        if (next.length() > MAX_EXPRESSION_LENGTH) {
            next = next.substring(0, MAX_EXPRESSION_LENGTH);
        }
        return next;
    }

    private String clipError(String error) {
        return error.length() <= 42 ? error : error.substring(0, 39) + "...";
    }

    private Map<String, Double> readVariables() {
        Map<String, Double> variables = new HashMap<>();
        variables.put("a", getInputDouble(INPUT_A_ID));
        variables.put("b", getInputDouble(INPUT_B_ID));
        variables.put("c", getInputDouble(INPUT_C_ID));
        variables.put("x", getInputDouble(INPUT_X_ID));
        variables.put("y", getInputDouble(INPUT_Y_ID));
        variables.put("z", getInputDouble(INPUT_Z_ID));
        variables.put("t", getInputDouble(INPUT_T_ID));
        variables.put("pi", Math.PI);
        variables.put("tau", Math.PI * 2.0d);
        variables.put("e", Math.E);
        return variables;
    }

    private double getInputDouble(String portId) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : 0.0d;
    }

    private void updateOutput(double result, boolean valid, String error) {
        lastError = error == null ? "" : error;
        outputValues.put(OUTPUT_RESULT_ID, result);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, lastError);
        syncOutputPorts();
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        String next = normalizeExpression(expression);
        if (!next.equals(this.expression)) {
            this.expression = next;
            bufferNeedsSync = true;
            markDirty();
        }
    }

    public boolean isShowError() {
        return showError;
    }

    public void setShowError(boolean showError) {
        if (this.showError != showError) {
            this.showError = showError;
            invalidateCache();
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("expression", expression);
        state.put("showError", showError);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.get("expression") instanceof String value) {
                expression = normalizeExpression(value);
            }
            if (map.get("showError") instanceof Boolean value) {
                showError = value;
            }
        } else if (state instanceof String value) {
            expression = normalizeExpression(value);
        }
        bufferNeedsSync = true;
        invalidateCache();
        markDirty();
    }

    private static final class Parser {
        private final String text;
        private final Map<String, Double> variables;
        private int index;
        private int recursionDepth;

        private Parser(String text, Map<String, Double> variables) {
            this.text = text == null ? "" : text;
            this.variables = variables;
        }

        private double parse() {
            if (text.isBlank()) {
                throw new ExpressionEvaluationException("Expression is empty");
            }
            if (text.length() > MAX_EXPRESSION_LENGTH) {
                throw new ExpressionEvaluationException(
                    "Expression is too long (max " + MAX_EXPRESSION_LENGTH + " characters)"
                );
            }
            double value = parseExpression();
            skipWhitespace();
            if (!isAtEnd()) {
                throw error("Unexpected token '" + currentChar() + "'");
            }
            return value;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (true) {
                skipWhitespace();
                if (match('+')) {
                    value += parseTerm();
                } else if (match('-')) {
                    value -= parseTerm();
                } else {
                    return value;
                }
            }
        }

        private double parseTerm() {
            double value = parsePower();
            while (true) {
                skipWhitespace();
                if (match('*')) {
                    value *= parsePower();
                } else if (match('/')) {
                    double divisor = parsePower();
                    if (Math.abs(divisor) <= 1.0e-12d) {
                        throw error("Division by zero");
                    }
                    value /= divisor;
                } else if (match('%')) {
                    double divisor = parsePower();
                    if (Math.abs(divisor) <= 1.0e-12d) {
                        throw error("Modulo by zero");
                    }
                    value %= divisor;
                } else {
                    return value;
                }
            }
        }

        private double parsePower() {
            double base = parseUnary();
            skipWhitespace();
            if (match('^')) {
                enterRecursion();
                try {
                    double exponent = parsePower();
                    return Math.pow(base, exponent);
                } finally {
                    exitRecursion();
                }
            }
            return base;
        }

        private double parseUnary() {
            skipWhitespace();
            if (match('+')) {
                enterRecursion();
                try {
                    return parseUnary();
                } finally {
                    exitRecursion();
                }
            }
            if (match('-')) {
                enterRecursion();
                try {
                    return -parseUnary();
                } finally {
                    exitRecursion();
                }
            }
            return parsePrimary();
        }

        private double parsePrimary() {
            skipWhitespace();
            if (match('(')) {
                enterRecursion();
                try {
                    double value = parseExpression();
                    expect(')');
                    return value;
                } finally {
                    exitRecursion();
                }
            }
            if (isIdentifierStart(currentCharOrZero())) {
                return parseIdentifier();
            }
            return parseNumber();
        }

        private double parseIdentifier() {
            String name = readIdentifier().toLowerCase(Locale.ROOT);
            skipWhitespace();
            if (match('(')) {
                List<Double> args = new ArrayList<>();
                skipWhitespace();
                if (!match(')')) {
                    do {
                        args.add(parseExpression());
                        skipWhitespace();
                    } while (match(','));
                    expect(')');
                }
                return callFunction(name, args);
            }
            Double value = variables.get(name);
            if (value == null) {
                throw error("Unknown variable '" + name + "'");
            }
            return value;
        }

        private double parseNumber() {
            skipWhitespace();
            int start = index;
            boolean sawDigit = false;

            while (!isAtEnd() && Character.isDigit(currentChar())) {
                index++;
                sawDigit = true;
            }
            if (!isAtEnd() && currentChar() == '.') {
                index++;
                while (!isAtEnd() && Character.isDigit(currentChar())) {
                    index++;
                    sawDigit = true;
                }
            }
            if (!sawDigit) {
                throw error("Expected number");
            }
            if (!isAtEnd() && (currentChar() == 'e' || currentChar() == 'E')) {
                int exponentStart = index;
                index++;
                if (!isAtEnd() && (currentChar() == '+' || currentChar() == '-')) {
                    index++;
                }
                boolean exponentDigits = false;
                while (!isAtEnd() && Character.isDigit(currentChar())) {
                    index++;
                    exponentDigits = true;
                }
                if (!exponentDigits) {
                    index = exponentStart;
                }
            }

            try {
                return Double.parseDouble(text.substring(start, index));
            } catch (NumberFormatException e) {
                throw error("Invalid number");
            }
        }

        private double callFunction(String name, List<Double> args) {
            return switch (name) {
                case "sin" -> one(name, args, Math::sin);
                case "cos" -> one(name, args, Math::cos);
                case "tan" -> one(name, args, Math::tan);
                case "asin" -> one(name, args, Math::asin);
                case "acos" -> one(name, args, Math::acos);
                case "atan" -> one(name, args, Math::atan);
                case "sqrt" -> one(name, args, Math::sqrt);
                case "abs" -> one(name, args, Math::abs);
                case "floor" -> one(name, args, Math::floor);
                case "ceil", "ceiling" -> one(name, args, Math::ceil);
                case "round" -> one(name, args, v -> (double) Math.round(v));
                case "log", "ln" -> one(name, args, Math::log);
                case "log10" -> one(name, args, Math::log10);
                case "exp" -> one(name, args, Math::exp);
                case "deg" -> one(name, args, Math::toDegrees);
                case "rad" -> one(name, args, Math::toRadians);
                case "pow" -> two(name, args, Math::pow);
                case "atan2" -> two(name, args, Math::atan2);
                case "min" -> min(args);
                case "max" -> max(args);
                case "clamp" -> clamp(args);
                case "lerp" -> lerp(args);
                case "smoothstep" -> smoothstep(args);
                default -> throw error("Unknown function '" + name + "'");
            };
        }

        private double one(String name, List<Double> args, UnaryFunction function) {
            if (args.size() != 1) {
                throw error(name + " expects 1 argument");
            }
            return function.apply(args.get(0));
        }

        private double two(String name, List<Double> args, BinaryFunction function) {
            if (args.size() != 2) {
                throw error(name + " expects 2 arguments");
            }
            return function.apply(args.get(0), args.get(1));
        }

        private double min(List<Double> args) {
            if (args.isEmpty()) {
                throw error("min expects at least 1 argument");
            }
            double value = args.get(0);
            for (int i = 1; i < args.size(); i++) {
                value = Math.min(value, args.get(i));
            }
            return value;
        }

        private double max(List<Double> args) {
            if (args.isEmpty()) {
                throw error("max expects at least 1 argument");
            }
            double value = args.get(0);
            for (int i = 1; i < args.size(); i++) {
                value = Math.max(value, args.get(i));
            }
            return value;
        }

        private double clamp(List<Double> args) {
            if (args.size() != 3) {
                throw error("clamp expects 3 arguments");
            }
            double min = Math.min(args.get(1), args.get(2));
            double max = Math.max(args.get(1), args.get(2));
            return Math.max(min, Math.min(max, args.get(0)));
        }

        private double lerp(List<Double> args) {
            if (args.size() != 3) {
                throw error("lerp expects 3 arguments");
            }
            return args.get(0) + (args.get(1) - args.get(0)) * args.get(2);
        }

        private double smoothstep(List<Double> args) {
            if (args.size() == 1) {
                double t = clamp01(args.get(0));
                return t * t * (3.0d - 2.0d * t);
            }
            if (args.size() == 3) {
                double edge0 = args.get(0);
                double edge1 = args.get(1);
                if (Math.abs(edge1 - edge0) <= 1.0e-12d) {
                    throw error("smoothstep edges cannot match");
                }
                double t = clamp01((args.get(2) - edge0) / (edge1 - edge0));
                return t * t * (3.0d - 2.0d * t);
            }
            throw error("smoothstep expects 1 or 3 arguments");
        }

        private double clamp01(double value) {
            return Math.max(0.0d, Math.min(1.0d, value));
        }

        private String readIdentifier() {
            int start = index;
            index++;
            while (!isAtEnd() && isIdentifierPart(currentChar())) {
                index++;
            }
            return text.substring(start, index);
        }

        private void expect(char c) {
            skipWhitespace();
            if (!match(c)) {
                throw error("Expected '" + c + "'");
            }
        }

        private boolean match(char c) {
            if (!isAtEnd() && currentChar() == c) {
                index++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (!isAtEnd() && Character.isWhitespace(currentChar())) {
                index++;
            }
        }

        private char currentChar() {
            return text.charAt(index);
        }

        private char currentCharOrZero() {
            return isAtEnd() ? '\0' : currentChar();
        }

        private boolean isAtEnd() {
            return index >= text.length();
        }

        private boolean isIdentifierStart(char c) {
            return Character.isLetter(c) || c == '_';
        }

        private boolean isIdentifierPart(char c) {
            return Character.isLetterOrDigit(c) || c == '_';
        }

        private ExpressionEvaluationException error(String message) {
            return new ExpressionEvaluationException(message + " at " + Math.min(index + 1, text.length()));
        }

        private void enterRecursion() {
            if (++recursionDepth > MAX_PARSE_DEPTH) {
                throw new ExpressionEvaluationException(
                    "Expression nesting is too deep (max " + MAX_PARSE_DEPTH + ")"
                );
            }
        }

        private void exitRecursion() {
            recursionDepth--;
        }

        @FunctionalInterface
        private interface UnaryFunction {
            double apply(double value);
        }

        @FunctionalInterface
        private interface BinaryFunction {
            double apply(double a, double b);
        }
    }
}
