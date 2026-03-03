package com.nodecraft.gui.editor.impl;

import com.nodecraft.core.NodeCraft; // 假设您有这个日志工具
import com.nodecraft.gui.editor.base.INodeEditor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW; // 引入 GLFW 鼠标按键常量

import java.util.*;

/**
 * 使用原生Minecraft GUI实现节点编辑器。
 * 这是一个简化的回退编辑器，用于演示基本的节点渲染和交互。
 */
public class NativeNodeEditor implements INodeEditor {

    // --- 单例模式实现 ---
    private static NativeNodeEditor INSTANCE;

    public static NativeNodeEditor getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new NativeNodeEditor();
        }
        return INSTANCE;
    }

    // 定义上下文键，用于保存/恢复状态
    private static final String CTX_CANVAS_X = "nativeEditor.canvasX";
    private static final String CTX_CANVAS_Y = "nativeEditor.canvasY";
    private static final String CTX_CANVAS_SCALE = "nativeEditor.canvasScale";

    private final TextRenderer textRenderer;
    private float canvasX = 0f;
    private float canvasY = 0f;
    private float canvasScale = 1.0f;
    private final List<EditorNode> nodes = new ArrayList<>();
    private EditorNode selectedNode = null;
    private boolean isOpen = false;

    // --- 交互状态 ---
    private boolean isDraggingCanvas = false; // 是否正在拖动画布
    private double lastMouseX; // 上一次鼠标X位置
    private double lastMouseY; // 上一次鼠标Y位置
    private EditorNode draggingNode = null; // 正在拖动的节点
    private double dragOffsetX; // 拖动开始时鼠标相对于节点原点的偏移
    private double dragOffsetY;

    // 鼠标滚轮缓存
    private double pendingScrollDelta = 0.0;

    // 私有构造函数，确保通过 getInstance() 获取实例
    private NativeNodeEditor() {
        this.textRenderer = MinecraftClient.getInstance().textRenderer;
        // 在构造时尝试恢复状态
        restoreState();
        NodeCraft.LOGGER.info("NativeNodeEditor 已构造。");
    }

    @Override
    public String getIdentifier() {
        return "native";
    }

    @Override
    public int getPriority() {
        return 100; // 较低优先级，作为回退选项
    }

    @Override
    public boolean isPlatformSupported() {
        return true; // 原生编辑器总是受支持
    }

    @Override
    public void init() {
        if (nodes.isEmpty()) {
            createExampleNodes();
        }
        NodeCraft.LOGGER.info("NativeNodeEditor 已初始化。");
    }

    @Override
    public void open() {
        isOpen = true;
        // 打开时恢复状态
        restoreState();
        NodeCraft.LOGGER.info("NativeNodeEditor 已打开。");
    }

    @Override
    public void close() {
        isOpen = false;
        // 关闭时保存状态
        saveState();
        NodeCraft.LOGGER.info("NativeNodeEditor 已关闭。");
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    private void saveState() {
        EditorContext.putState(CTX_CANVAS_X, canvasX);
        EditorContext.putState(CTX_CANVAS_Y, canvasY);
        EditorContext.putState(CTX_CANVAS_SCALE, canvasScale);
        NodeCraft.LOGGER.info("NativeNodeEditor 状态已保存。");
    }

    private void restoreState() {
        canvasX = EditorContext.getStateOrDefault(CTX_CANVAS_X, Float.class, 0f);
        canvasY = EditorContext.getStateOrDefault(CTX_CANVAS_Y, Float.class, 0f);
        canvasScale = EditorContext.getStateOrDefault(CTX_CANVAS_SCALE, Float.class, 1.0f);
        NodeCraft.LOGGER.info("NativeNodeEditor 状态已恢复: X={}, Y={}, Scale={}", canvasX, canvasY, canvasScale);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isOpen) return;
        MinecraftClient client = MinecraftClient.getInstance();
        Screen currentScreen = client.currentScreen;
        if (currentScreen == null) return;

        // --- 处理鼠标输入 ---
        handleMouseInput(mouseX, mouseY, client.mouse.wasLeftButtonClicked(), client.mouse.wasRightButtonClicked());

        // --- 渲染变换 ---
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(canvasX, canvasY);
        context.getMatrices().scale(canvasScale, canvasScale);

        // --- 渲染元素 ---
        renderGrid(context, currentScreen.width, currentScreen.height);
        renderConnections(context);
        renderNodes(context, mouseX, mouseY); // 传入原始鼠标坐标以便计算悬停

        context.getMatrices().popMatrix();

        // --- 渲染UI元素（不受画布变换影响） ---
        renderUI(context, mouseX, mouseY, currentScreen.width, currentScreen.height);
    }

    /**
     * 处理鼠标滚轮事件。
     * 这个方法应该被 NodecraftScreen 调用来处理缩放。
     * @param mouseX 鼠标X屏幕坐标
     * @param mouseY 鼠标Y屏幕坐标
     * @param horizontalAmount 水平滚动量
     * @param verticalAmount 垂直滚动量
     * @return 是否处理了该事件
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isOpen) return false;
        
        // 处理垂直滚轮缩放
        if (verticalAmount != 0) {
            float zoomFactor = 1.0f + (float) (verticalAmount * 0.1f); // 缩放因子
            float oldScale = canvasScale;
            canvasScale *= zoomFactor;

            // 限制缩放范围
            canvasScale = Math.max(0.5f, Math.min(2.0f, canvasScale));

            // 保持缩放中心在鼠标位置
            // 先将鼠标屏幕坐标转换为当前缩放下的世界坐标
            float worldMouseX = (float)(mouseX - canvasX) / oldScale;
            float worldMouseY = (float)(mouseY - canvasY) / oldScale;

            // 重新计算画布偏移以保持鼠标位置在世界坐标下不变
            canvasX = (float)(mouseX - worldMouseX * canvasScale);
            canvasY = (float)(mouseY - worldMouseY * canvasScale);

            NodeCraft.LOGGER.debug("NativeEditor: 缩放: {}, 新缩放: {}", zoomFactor, canvasScale);
            return true;
        }
        
        return false;
    }

    /**
     * 处理鼠标输入事件，包括选择、拖拽和画布平移。
     * @param mouseX 鼠标X屏幕坐标。
     * @param mouseY 鼠标Y屏幕坐标。
     * @param leftButtonClicked 鼠标左键是否刚刚点击。
     * @param rightButtonClicked 鼠标右键是否刚刚点击。
     */
    private void handleMouseInput(int mouseX, int mouseY, boolean leftButtonClicked, boolean rightButtonClicked) {
        // 将屏幕鼠标坐标转换为画布世界坐标
        float canvasMouseX = (mouseX - canvasX) / canvasScale;
        float canvasMouseY = (mouseY - canvasY) / canvasScale;

        // --- 鼠标左键点击和拖拽逻辑 ---
        if (leftButtonClicked) { // 鼠标左键刚刚点击
            boolean clickedOnNode = false;
            for (EditorNode node : nodes) {
                if (canvasMouseX >= node.x && canvasMouseX <= node.x + node.width &&
                        canvasMouseY >= node.y && canvasMouseY <= node.y + node.height) {
                    // 点击到节点
                    selectedNode = node; // 选中节点
                    draggingNode = node; // 准备拖动节点
                    dragOffsetX = canvasMouseX - node.x; // 记录鼠标与节点原点的偏移
                    dragOffsetY = canvasMouseY - node.y;
                    isDraggingCanvas = false; // 确保不拖动画布
                    clickedOnNode = true;
                    NodeCraft.LOGGER.debug("NativeEditor: 选中并准备拖动节点: {}", node.title);
                    break;
                }
            }

            if (!clickedOnNode) { // 点击到画布空白处
                selectedNode = null; // 取消选中任何节点
                isDraggingCanvas = true; // 准备拖动画布
                NodeCraft.LOGGER.debug("NativeEditor: 点击画布空白处，准备平移。");
            }
            lastMouseX = mouseX; // 记录当前鼠标位置作为拖动起始
            lastMouseY = mouseY;

        } else if (GLFW.glfwGetMouseButton(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS) { // 鼠标左键持续按下
            if (draggingNode != null) { // 正在拖动节点
                draggingNode.x = (int) (canvasMouseX - dragOffsetX);
                draggingNode.y = (int) (canvasMouseY - dragOffsetY);
            } else if (isDraggingCanvas) { // 正在拖动画布
                canvasX += (mouseX - lastMouseX);
                canvasY += (mouseY - lastMouseY);
            }
            lastMouseX = mouseX;
            lastMouseY = mouseY;

        } else if (GLFW.glfwGetMouseButton(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_RELEASE) { // 鼠标左键抬起
            draggingNode = null; // 停止拖动节点
            isDraggingCanvas = false; // 停止拖动画布
        }
    }

    private void createExampleNodes() {
        nodes.add(new EditorNode("input_node", "输入节点", 100, 100, 150, 100));
        nodes.add(new EditorNode("transform_node", "变换节点", 300, 150, 150, 120));
        nodes.add(new EditorNode("output_node", "输出节点", 500, 100, 150, 100));
        NodeCraft.LOGGER.info("NativeEditor: 已创建示例节点。");
    }

    private void renderGrid(DrawContext context, int screenWidth, int screenHeight) {
        int gridSize = 20;
        int gridColor = 0x22FFFFFF; // 半透明白色

        // 计算可见网格区域的世界坐标范围
        float viewWorldMinX = -canvasX / canvasScale;
        float viewWorldMinY = -canvasY / canvasScale;
        float viewWorldMaxX = (screenWidth - canvasX) / canvasScale;
        float viewWorldMaxY = (screenHeight - canvasY) / canvasScale;

        // 计算网格线的起始和结束位置 (按网格大小对齐)
        int startX = (int) (Math.floor(viewWorldMinX / gridSize) * gridSize);
        int startY = (int) (Math.floor(viewWorldMinY / gridSize) * gridSize);
        int endX = (int) (Math.ceil(viewWorldMaxX / gridSize) * gridSize);
        int endY = (int) (Math.ceil(viewWorldMaxY / gridSize) * gridSize);

        for (int x = startX; x <= endX; x += gridSize) {
            // drawVerticalLine 接收的是屏幕像素坐标
            context.drawVerticalLine(x, startY, endY, gridColor);
        }
        for (int y = startY; y <= endY; y += gridSize) {
            context.drawHorizontalLine(startX, endX, y, gridColor);
        }
    }

    private void renderConnections(DrawContext context) {
        // 绘制简单连接线，不使用贝塞尔曲线
        if (nodes.size() >= 3) {
            // 连接输入节点到变换节点
            drawLine(context, nodes.get(0).x + nodes.get(0).width, nodes.get(0).y + 40, nodes.get(1).x, nodes.get(1).y + 40, 0xFF6666FF);
            // 连接变换节点到输出节点
            drawLine(context, nodes.get(1).x + nodes.get(1).width, nodes.get(1).y + 40, nodes.get(2).x, nodes.get(2).y + 40, 0xFF66FF66);
        }
    }

    // 绘制直线的方法，替代之前复杂的贝塞尔曲线近似
    private void drawLine(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        context.drawHorizontalLine(Math.min(x1, x2), Math.max(x1, x2), y1, color); // 从 x1 到 x2 的水平线
        context.drawVerticalLine(x2, Math.min(y1, y2), Math.max(y1, y2), color); // 从 y1 到 y2 的垂直线 (在 x2 处)
        // 这是一个 L 形连接，如果需要更平滑的直线，请使用更复杂的算法，或直接使用 DrawContext 的 drawLine 方法（如果支持）
        // DrawContext.drawLine 接收的是屏幕坐标，但此处在变换矩阵之后，是世界坐标
        // 实际上 drawHorizontalLine 和 drawVerticalLine 已经是 DrawContext.drawHorizontalLine/drawVerticalLine
        // 绘制折线连接，而不是近似贝塞尔曲线
    }

    private void renderNodes(DrawContext context, int mouseX, int mouseY) {
        for (EditorNode node : nodes) {
            // 将屏幕鼠标坐标转换为画布世界坐标（相对于当前变换矩阵）
            float canvasMouseX = (mouseX - canvasX) / canvasScale;
            float canvasMouseY = (mouseY - canvasY) / canvasScale;

            boolean isHovered = canvasMouseX >= node.x && canvasMouseX <= node.x + node.width &&
                    canvasMouseY >= node.y && canvasMouseY <= node.y + node.height;
            boolean isSelected = node == selectedNode;
            renderNode(context, node, isHovered, isSelected);
        }
    }

    private void renderNode(DrawContext context, EditorNode node, boolean isHovered, boolean isSelected) {
        int backgroundColor = isSelected ? 0xFF3C3C3C : (isHovered ? 0xFF2C2C2C : 0xFF1C1C1C);
        int borderColor = isSelected ? 0xFFAAAAAA : (isHovered ? 0xFF888888 : 0xFF666666);

        // 绘制节点背景和边框
        context.fill(node.x, node.y, node.x + node.width, node.y + node.height, backgroundColor);
        context.drawStrokedRectangle(node.x, node.y, node.width, node.height, borderColor);

        // 绘制节点标题
        int titleColor = 0xFFFFFFFF;
        context.drawCenteredTextWithShadow(textRenderer, node.title, node.x + node.width / 2, node.y + 10, titleColor);

        // 绘制输入/输出端口 (简单的圆形表示)
        // 输入端口（左侧）
        context.fill(node.x - 5, node.y + 40 - 5, node.x + 5, node.y + 40 + 5, 0xFF6666FF); // 蓝色端口
        // 输出端口（右侧）
        context.fill(node.x + node.width - 5, node.y + 40 - 5, node.x + node.width + 5, node.y + 40 + 5, 0xFF66FF66); // 绿色端口
    }

    private void renderUI(DrawContext context, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        int toolbarHeight = 30;
        context.fill(0, screenHeight - toolbarHeight, screenWidth, screenHeight, 0xAA000000); // 底部工具栏
        String infoText = String.format("缩放: %.2fx | 位置: %.0f, %.0f | 鼠标: %d, %d", canvasScale, canvasX, canvasY, mouseX, mouseY);
        context.drawTextWithShadow(textRenderer, infoText, 10, screenHeight - 20, 0xFFCCCCCC);
    }

    /**
     * 内部类：表示原生编辑器中的一个节点。
     * 这是一个简化的节点模型，不包含端口连接逻辑。
     */
    private static class EditorNode {
        public String id;
        public String title;
        public int x;
        public int y;
        public int width;
        public int height;

        public EditorNode(String id, String title, int x, int y, int width, int height) {
            this.id = id;
            this.title = title;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    // --- 内部类：模拟 EditorContext，用于保存/恢复状态 ---
    // 在真实项目中，这会是 NodeCraft 核心库中的一个实际类
    public static class EditorContext {
        private static final Map<String, Object> stateMap = new HashMap<>();

        public static <T> void putState(String key, T value) {
            stateMap.put(key, value);
        }

        public static <T> T getStateOrDefault(String key, Class<T> type, T defaultValue) {
            Object value = stateMap.get(key);
            if (type.isInstance(value)) {
                return type.cast(value);
            }
            return defaultValue;
        }
    }
}