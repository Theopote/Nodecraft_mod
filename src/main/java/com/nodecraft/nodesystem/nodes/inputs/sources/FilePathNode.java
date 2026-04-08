package com.nodecraft.nodesystem.nodes.inputs.sources;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.gui.editor.impl.ZoomHelper;
import com.nodecraft.gui.dialogs.FileDialogManager;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.type.ImString;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 文件路径节点，提供文本输入框来编辑文件或目录路径。
 */
@NodeInfo(
    id = "inputs.sources.file_path",
    displayName = "文件路径",
    description = "用于输入文件或目录路径",
    category = "inputs.sources"
)
public class FilePathNode extends BaseCustomUINode {
    
    @NodeProperty(displayName = "文件路径", category = "路径", order = 1,
                  description = "文件或目录的路径")
    private volatile String filePath = "";

    @NodeProperty(displayName = "必须存在", category = "验证", order = 10,
                  description = "文件是否必须存在")
    private boolean mustExist = false;

    @NodeProperty(displayName = "是目录", category = "设置", order = 11,
                  description = "是否为目录路径")
    private boolean isDirectory = false;
    
    // --- 输出端口 ---
    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_EXISTS_ID = "output_exists";
    private static final String OUTPUT_FILENAME_ID = "output_filename";
    private static final String OUTPUT_EXTENSION_ID = "output_extension";
    private static final String OUTPUT_DIRECTORY_ID = "output_directory";
    
    // --- UI状态 ---
    private transient ImString pathBuffer = new ImString(1024);
    private transient volatile boolean bufferNeedsSync = true;
    
    public FilePathNode() {
        super(UUID.randomUUID(), "inputs.sources.file_path");
        
        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "The full file or directory path", NodeDataType.FILE_PATH, this));
        addOutputPort(new BasePort(OUTPUT_EXISTS_ID, "Exists", "Whether the file/directory exists", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_FILENAME_ID, "Filename", "The filename without extension", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_EXTENSION_ID, "Extension", "The file extension", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_DIRECTORY_ID, "Directory", "The containing directory", NodeDataType.STRING, this));
    }
    
    @Override
    public String getDescription() { return "输入文件或目录路径"; }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (filePath == null || filePath.isEmpty()) {
            outputValues.put(OUTPUT_PATH_ID, "");
            outputValues.put(OUTPUT_EXISTS_ID, false);
            outputValues.put(OUTPUT_FILENAME_ID, "");
            outputValues.put(OUTPUT_EXTENSION_ID, "");
            outputValues.put(OUTPUT_DIRECTORY_ID, "");
            return;
        }
        
        File file = new File(filePath);
        boolean exists = file.exists();
        String filename = "", extension = "", directory = "";
        
        try {
            Path path = Paths.get(filePath);
            Path fileName = path.getFileName();
            Path parent = path.getParent();
            if (fileName != null) {
                String fileNameStr = fileName.toString();
                int lastDot = fileNameStr.lastIndexOf('.');
                if (lastDot > 0) {
                    filename = fileNameStr.substring(0, lastDot);
                    extension = fileNameStr.substring(lastDot + 1);
                } else {
                    filename = fileNameStr;
                }
            }
            if (parent != null) directory = parent.toString();
        } catch (Exception e) { /* 无效路径 */ }
        
        outputValues.put(OUTPUT_PATH_ID, filePath);
        outputValues.put(OUTPUT_EXISTS_ID, exists);
        outputValues.put(OUTPUT_FILENAME_ID, filename);
        outputValues.put(OUTPUT_EXTENSION_ID, extension);
        outputValues.put(OUTPUT_DIRECTORY_ID, directory);
    }
    
    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += ImGui.getFrameHeight();
        height += getMediumPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 208f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            try {
                float edgeMargin = ZoomHelper.applyZoom(getMediumPadding(), zoom);
                float availableWidth = Math.max(0.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
                float gap = ZoomHelper.applyZoom(getSmallPadding(), zoom);
                float browseButtonWidth = Math.max(
                    ZoomHelper.applyZoom(28.0f, zoom),
                    ImGui.calcTextSize("...").x + ZoomHelper.applyZoom(14.0f, zoom)
                );
                float inputWidth = Math.max(0.0f, availableWidth - browseButtonWidth - gap);
                float baseCursorX = ImGui.getCursorPosX();

                l.addVerticalSpacing(getMediumPadding());

                ensureBuffer();
                ImGui.setCursorPosX(baseCursorX + edgeMargin);
                l.pushFramePadding(4.0f, 3.0f);
                ImGui.pushItemWidth(inputWidth);
                
                if (ImGui.inputTextWithHint("##file_path", "输入路径...", pathBuffer)) {
                    String newPath = pathBuffer.get().trim();
                    if (!newPath.equals(filePath)) {
                        setFilePath(newPath);
                        changed = true;
                    }
                }
                
                ImGui.popItemWidth();
                ImGui.sameLine(0.0f, gap);
                if (ImGui.button("...", browseButtonWidth, 0.0f)) {
                    Path initialDir;
                    try {
                        if (filePath != null && !filePath.isBlank()) {
                            Path currentPath = Paths.get(filePath);
                            if (java.nio.file.Files.isDirectory(currentPath)) {
                                initialDir = currentPath;
                            } else {
                                Path parent = currentPath.getParent();
                                initialDir = parent != null ? parent : Paths.get("").toAbsolutePath();
                            }
                        } else {
                            initialDir = Paths.get("").toAbsolutePath();
                        }
                    } catch (Exception ignored) {
                        initialDir = Paths.get("").toAbsolutePath();
                    }

                    FileDialogManager.showFileDialog(
                        isDirectory ? "Select Directory" : "Select File",
                        initialDir,
                        "",
                        false,
                        "",
                        selectedPath -> {
                            if (selectedPath != null) {
                                setFilePath(selectedPath.toString());
                            }
                        }
                    );
                    changed = true;
                }
                l.popStyleVar();

                l.addVerticalSpacing(getMediumPadding());
            } catch (Exception e) {
                System.err.println("FilePathNode UI渲染失败: " + e.getMessage());
            }
            return changed;
        });
    }
    
    private void ensureBuffer() {
        if (pathBuffer == null) pathBuffer = new ImString(1024);
        if (bufferNeedsSync) {
            pathBuffer.set(filePath != null ? filePath : "");
            bufferNeedsSync = false;
        }
    }
    
    public void setFilePath(String path) {
        if (path == null) path = "";
        if (!this.filePath.equals(path)) {
            this.filePath = path;
            bufferNeedsSync = true;
            markDirty();
        }
    }
    
    public String getFilePath() { return filePath; }
    public boolean isMustExist() { return mustExist; }
    public void setMustExist(boolean mustExist) { this.mustExist = mustExist; }
    public boolean isDirectory() { return isDirectory; }
    public void setDirectory(boolean directory) { this.isDirectory = directory; }
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("filePath", getFilePath());
        state.put("mustExist", isMustExist());
        state.put("isDirectory", isDirectory());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> m = (java.util.Map<?, ?>) state;
            if (m.containsKey("mustExist")) {
                Object v = m.get("mustExist");
                if (v instanceof Boolean) setMustExist((Boolean) v);
            }
            if (m.containsKey("isDirectory")) {
                Object v = m.get("isDirectory");
                if (v instanceof Boolean) setDirectory((Boolean) v);
            }
            if (m.containsKey("filePath")) {
                Object v = m.get("filePath");
                if (v instanceof String) setFilePath((String) v);
            }
        }
    }
}
