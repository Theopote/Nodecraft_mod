package com.nodecraft.gui.window;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.integration.ImGuiGLStateGuard;
import com.nodecraft.gui.editor.integration.ImGuiRenderer;
import com.nodecraft.gui.screens.NodecraftScreen;
import imgui.ImDrawData;
import imgui.ImGuiIO;
import imgui.gl3.ImGuiImplGl3;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class DetachedEditorWindow {
    private static final String WINDOW_TITLE = "NodeCraft Editor";

    public static boolean hasMultipleMonitors() {
        final PointerBuffer monitors = GLFW.glfwGetMonitors();
        return monitors != null && monitors.limit() > 1;
    }

    private long windowHandle;
    private NodecraftScreen attachedScreen;
    private boolean previousLeftMouseDown;
    private boolean previousRightMouseDown;
    private boolean currentLeftMouseDown;
    private boolean currentRightMouseDown;
    private boolean currentMiddleMouseDown;
    private boolean leftMouseClickedThisFrame;
    private boolean rightMouseClickedThisFrame;
    private boolean middleMouseClickedThisFrame;
    private boolean leftMouseReleasedThisFrame;
    private boolean rightMouseReleasedThisFrame;
    private boolean middleMouseReleasedThisFrame;

    public boolean isDetached(final NodecraftScreen screen) {
        return windowHandle != 0 && attachedScreen == screen;
    }

    public boolean isOpen() {
        return windowHandle != 0 && attachedScreen != null;
    }

    public long getWindowHandle() {
        return windowHandle;
    }

    public void open(final NodecraftScreen screen) {
        if (screen == null) {
            return;
        }

        if (isDetached(screen)) {
            GLFW.glfwShowWindow(windowHandle);
            GLFW.glfwFocusWindow(windowHandle);
            return;
        }

        cleanup();

        final MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            NodeCraft.LOGGER.warn("Minecraft window is unavailable, cannot detach editor");
            return;
        }

        final long primaryWindowHandle = client.getWindow().getHandle();
        if (primaryWindowHandle == 0) {
            NodeCraft.LOGGER.warn("Primary GLFW window handle is invalid, cannot detach editor");
            return;
        }

        final int[] posX = new int[1];
        final int[] posY = new int[1];
        final int[] width = new int[1];
        final int[] height = new int[1];
        final int[] workareaX = new int[1];
        final int[] workareaY = new int[1];
        final int[] workareaWidth = new int[1];
        final int[] workareaHeight = new int[1];
        pickWindowPlacement(posX, posY, width, height, workareaX, workareaY, workareaWidth, workareaHeight);

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_TRUE);

        windowHandle = GLFW.glfwCreateWindow(width[0], height[0], WINDOW_TITLE, 0, primaryWindowHandle);
        if (windowHandle == 0) {
            NodeCraft.LOGGER.error("Failed to create detached editor window");
            return;
        }

        final long previousGlContext = GLFW.glfwGetCurrentContext();

        try {
            GLFW.glfwMakeContextCurrent(windowHandle);
            GLFW.glfwSetWindowSize(windowHandle, width[0], height[0]);
            GLFW.glfwSetWindowPos(windowHandle, posX[0], posY[0]);
            GLFW.glfwSwapInterval(0);

            attachedScreen = screen;
            previousLeftMouseDown = false;
            previousRightMouseDown = false;
            currentLeftMouseDown = false;
            currentRightMouseDown = false;
            currentMiddleMouseDown = false;
            leftMouseClickedThisFrame = false;
            rightMouseClickedThisFrame = false;
            middleMouseClickedThisFrame = false;
            leftMouseReleasedThisFrame = false;
            rightMouseReleasedThisFrame = false;
            middleMouseReleasedThisFrame = false;
            ImGuiRenderer.getInstance().bindPlatformBackendToWindow(windowHandle);
            GLFW.glfwShowWindow(windowHandle);
            fitWindowFrameIntoWorkarea(posX, posY, width, height, workareaX, workareaY, workareaWidth, workareaHeight);
            GLFW.glfwSetWindowSize(windowHandle, width[0], height[0]);
            GLFW.glfwSetWindowPos(windowHandle, posX[0], posY[0]);
            GLFW.glfwFocusWindow(windowHandle);
            NodeCraft.LOGGER.info("Detached editor window opened: {}", windowHandle);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to initialize detached editor window", e);
            cleanup();
        } finally {
            if (previousGlContext != 0) {
                GLFW.glfwMakeContextCurrent(previousGlContext);
            }
        }
    }

    private void pickWindowPlacement(
        final int[] posX,
        final int[] posY,
        final int[] width,
        final int[] height,
        final int[] workareaX,
        final int[] workareaY,
        final int[] workareaWidth,
        final int[] workareaHeight
    ) {
        final PointerBuffer monitors = GLFW.glfwGetMonitors();
        long targetMonitor = 0;

        if (monitors != null && monitors.limit() > 1) {
            targetMonitor = monitors.get(1);
        } else if (monitors != null && monitors.limit() > 0) {
            targetMonitor = monitors.get(0);
        }

        if (targetMonitor != 0) {
            GLFW.glfwGetMonitorWorkarea(targetMonitor, workareaX, workareaY, workareaWidth, workareaHeight);

            final int monitorWorkareaX = workareaX[0];
            final int monitorWorkareaY = workareaY[0];
            final int monitorWorkareaWidth = Math.max(1, workareaWidth[0]);
            final int monitorWorkareaHeight = Math.max(1, workareaHeight[0]);
            final int desiredWidth = 1440;
            final int desiredHeight = 900;
            final int minWidth = 960;
            final int minHeight = 640;

            width[0] = Math.max(Math.min(minWidth, monitorWorkareaWidth), Math.min(desiredWidth, monitorWorkareaWidth));
            height[0] = Math.max(Math.min(minHeight, monitorWorkareaHeight), Math.min(desiredHeight, monitorWorkareaHeight));
            posX[0] = monitorWorkareaX + Math.max(0, (monitorWorkareaWidth - width[0]) / 2);
            posY[0] = monitorWorkareaY + Math.max(0, (monitorWorkareaHeight - height[0]) / 2);
        } else {
            posX[0] = 80;
            posY[0] = 80;
            width[0] = 1440;
            height[0] = 900;
            workareaX[0] = posX[0];
            workareaY[0] = posY[0];
            workareaWidth[0] = width[0];
            workareaHeight[0] = height[0];
        }
    }

    private void fitWindowFrameIntoWorkarea(
        final int[] posX,
        final int[] posY,
        final int[] width,
        final int[] height,
        final int[] workareaX,
        final int[] workareaY,
        final int[] workareaWidth,
        final int[] workareaHeight
    ) {
        final int[] frameLeft = new int[1];
        final int[] frameTop = new int[1];
        final int[] frameRight = new int[1];
        final int[] frameBottom = new int[1];
        GLFW.glfwGetWindowFrameSize(windowHandle, frameLeft, frameTop, frameRight, frameBottom);

        final int maxClientWidth = Math.max(1, workareaWidth[0] - frameLeft[0] - frameRight[0]);
        final int maxClientHeight = Math.max(1, workareaHeight[0] - frameTop[0] - frameBottom[0]);

        width[0] = Math.min(width[0], maxClientWidth);
        height[0] = Math.min(height[0], maxClientHeight);

        final int outerWidth = width[0] + frameLeft[0] + frameRight[0];
        final int outerHeight = height[0] + frameTop[0] + frameBottom[0];

        // GLFW window position is the client-area top-left, not the outer frame top-left.
        // Offset by the frame metrics so the native title bar stays inside the monitor workarea.
        posX[0] = workareaX[0] + frameLeft[0] + Math.max(0, (workareaWidth[0] - outerWidth) / 2);
        posY[0] = workareaY[0] + frameTop[0] + Math.max(0, (workareaHeight[0] - outerHeight) / 2);

        posX[0] = Math.max(workareaX[0] + frameLeft[0], posX[0]);
        posY[0] = Math.max(workareaY[0] + frameTop[0], posY[0]);
    }

    public void prepareFrame(final ImGuiIO io) {
        if (!isOpen()) {
            return;
        }

        final int[] width = new int[1];
        final int[] height = new int[1];
        final int[] framebufferWidth = new int[1];
        final int[] framebufferHeight = new int[1];
        final double[] mouseX = new double[1];
        final double[] mouseY = new double[1];

        GLFW.glfwGetWindowSize(windowHandle, width, height);
        GLFW.glfwGetFramebufferSize(windowHandle, framebufferWidth, framebufferHeight);
        GLFW.glfwGetCursorPos(windowHandle, mouseX, mouseY);

        io.setDisplaySize(Math.max(1, width[0]), Math.max(1, height[0]));
        io.setDisplayFramebufferScale(
            Math.max(1.0f, (float) framebufferWidth[0] / Math.max(1.0f, width[0])),
            Math.max(1.0f, (float) framebufferHeight[0] / Math.max(1.0f, height[0]))
        );
        io.setMousePos((float) mouseX[0], (float) mouseY[0]);
        pollDetachedMouseButtons(io);
    }

    public void renderDrawData(final ImDrawData drawData, final ImGuiImplGl3 rendererBackend) {
        if (windowHandle == 0 || attachedScreen == null) {
            return;
        }

        if (GLFW.glfwWindowShouldClose(windowHandle)) {
            attachedScreen.requestClose();
            cleanup();
            return;
        }

        if (attachedScreen.getWindowRenderer() == null) {
            cleanup();
            return;
        }

        final long previousGlContext = GLFW.glfwGetCurrentContext();

        try {
            GLFW.glfwMakeContextCurrent(windowHandle);

            final int[] framebufferWidth = new int[1];
            final int[] framebufferHeight = new int[1];
            GLFW.glfwGetFramebufferSize(windowHandle, framebufferWidth, framebufferHeight);

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL11.glViewport(0, 0, Math.max(1, framebufferWidth[0]), Math.max(1, framebufferHeight[0]));
            GL11.glClearColor(0.06f, 0.07f, 0.09f, 1.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

            if (drawData != null && drawData.getCmdListsCount() > 0) {
                try (ImGuiGLStateGuard ignored = ImGuiGLStateGuard.enter()) {
                    rendererBackend.renderDrawData(drawData);
                }
            }

            GLFW.glfwSwapBuffers(windowHandle);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to render detached editor window", e);
            attachedScreen.requestClose();
            cleanup();
        } finally {
            if (previousGlContext != 0) {
                GLFW.glfwMakeContextCurrent(previousGlContext);
            }
        }
    }

    public void close(final NodecraftScreen screen) {
        if (screen == null || attachedScreen != screen) {
            return;
        }
        cleanup();
    }

    private void pollDetachedMouseButtons(final ImGuiIO io) {
        final boolean leftDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        final boolean rightDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        final boolean middleDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        leftMouseClickedThisFrame = leftDown && !currentLeftMouseDown;
        rightMouseClickedThisFrame = rightDown && !currentRightMouseDown;
        middleMouseClickedThisFrame = middleDown && !currentMiddleMouseDown;
        leftMouseReleasedThisFrame = !leftDown && currentLeftMouseDown;
        rightMouseReleasedThisFrame = !rightDown && currentRightMouseDown;
        middleMouseReleasedThisFrame = !middleDown && currentMiddleMouseDown;

        currentLeftMouseDown = leftDown;
        currentRightMouseDown = rightDown;
        currentMiddleMouseDown = middleDown;
        io.setMouseDown(0, leftDown);
        io.setMouseDown(1, rightDown);
        io.setMouseDown(2, middleDown);

        if (leftDown != previousLeftMouseDown || rightDown != previousRightMouseDown) {
            NodeCraft.LOGGER.info(
                "Detached input state changed: focused={}, hovered={}, leftDown={}, rightDown={}, mouse=({}, {}), wantCaptureMouse={}",
                GLFW.glfwGetWindowAttrib(windowHandle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE,
                GLFW.glfwGetWindowAttrib(windowHandle, GLFW.GLFW_HOVERED) == GLFW.GLFW_TRUE,
                leftDown,
                rightDown,
                io.getMousePosX(),
                io.getMousePosY(),
                io.getWantCaptureMouse()
            );
            previousLeftMouseDown = leftDown;
            previousRightMouseDown = rightDown;
        }
    }

    public boolean isMouseDown(final int button) {
        return switch (button) {
            case 0 -> currentLeftMouseDown;
            case 1 -> currentRightMouseDown;
            case 2 -> currentMiddleMouseDown;
            default -> false;
        };
    }

    public boolean isMouseClicked(final int button) {
        return switch (button) {
            case 0 -> leftMouseClickedThisFrame;
            case 1 -> rightMouseClickedThisFrame;
            case 2 -> middleMouseClickedThisFrame;
            default -> false;
        };
    }

    public boolean isMouseReleased(final int button) {
        return switch (button) {
            case 0 -> leftMouseReleasedThisFrame;
            case 1 -> rightMouseReleasedThisFrame;
            case 2 -> middleMouseReleasedThisFrame;
            default -> false;
        };
    }

    public void cleanup() {
        final long handleToDestroy = windowHandle;

        windowHandle = 0;
        attachedScreen = null;

        if (handleToDestroy != 0) {
            ImGuiRenderer.getInstance().restorePrimaryPlatformBackend();
            try {
                Callbacks.glfwFreeCallbacks(handleToDestroy);
            } catch (Exception e) {
                NodeCraft.LOGGER.debug("Detached editor callback free failed: {}", e.getMessage());
            }
            try {
                GLFW.glfwDestroyWindow(handleToDestroy);
            } catch (Exception e) {
                NodeCraft.LOGGER.debug("Detached editor window destroy failed: {}", e.getMessage());
            }
        }
    }
}
