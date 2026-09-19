package com.nodecraft.nodesystem.nodes.input.values;

import com.nodecraft.gui.dialogs.FileDialogManager;
import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.type.ImInt;
import imgui.type.ImString;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "input.values.file_path",
    displayName = "File Path Input",
    description = "Selects or types a local file path and outputs it for file read/write nodes.",
    category = "input.values",
    order = 11
)
public class FilePathInputNode extends BaseCustomUINode {

    public enum DialogMode {
        OPEN("Open"),
        SAVE("Save");

        private final String displayName;

        DialogMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_TEXT_ID = "output_text";
    private static final String OUTPUT_DIRECTORY_ID = "output_directory";
    private static final String OUTPUT_FILENAME_ID = "output_filename";
    private static final String OUTPUT_EXTENSION_ID = "output_extension";
    private static final String OUTPUT_HAS_VALUE_ID = "output_has_value";

    @NodeProperty(displayName = "Path", category = "Value", order = 1)
    private String selectedPath = "";

    @NodeProperty(displayName = "Mode", category = "Dialog", order = 2)
    private DialogMode dialogMode = DialogMode.OPEN;

    @NodeProperty(displayName = "Initial Directory", category = "Dialog", order = 3,
        description = "Directory shown first when opening the file picker")
    private String initialDirectory = ".";

    @NodeProperty(displayName = "Filter", category = "Dialog", order = 4,
        description = "Optional filter such as Image Files (*.png;*.jpg)")
    private String filter = "";

    @NodeProperty(displayName = "Default Extension", category = "Dialog", order = 5,
        description = "Extension appended by save dialog, such as .json")
    private String defaultExtension = "";

    public FilePathInputNode() {
        super(UUID.randomUUID(), "input.values.file_path");
        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "Selected file path", NodeDataType.FILE_PATH, this));
        addOutputPort(new BasePort(OUTPUT_TEXT_ID, "Text", "Selected file path as text", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_DIRECTORY_ID, "Directory", "Parent directory", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_FILENAME_ID, "File Name", "File name with extension", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_EXTENSION_ID, "Extension", "Lowercase file extension", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_HAS_VALUE_ID, "Has Value", "True when a non-empty path is selected", NodeDataType.BOOLEAN, this));
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "Selects or types a local file path and outputs it for file read/write nodes.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        updateOutput();
    }

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += ImGui.getFrameHeight();
        height += getSmallPadding();
        height += ImGui.getFrameHeight();
        height += getSmallPadding();
        height += ImGui.getFrameHeight();
        height += getMediumPadding();
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
            float availableWidth = l.getAvailableContentWidth(width);

            l.addVerticalSpacing(getMediumPadding());

            ImString pathInput = new ImString(normalizeString(selectedPath), 1024);
            l.setItemWidth(Math.max(availableWidth / Math.max(zoom, 0.001f), 1.0f));
            if (ImGui.inputText("##file_path_input", pathInput)) {
                setSelectedPath(pathInput.get());
                changed = true;
            }
            l.popItemWidth();
            l.addVerticalSpacing(getSmallPadding());

            float comboWidth = Math.min(l.toPixels(96.0f), availableWidth * 0.45f);
            l.setItemWidth(comboWidth / Math.max(zoom, 0.001f));
            ImInt modeIndex = new ImInt(dialogMode == DialogMode.OPEN ? 0 : 1);
            if (ImGui.combo("##file_path_mode", modeIndex, new String[]{"Open", "Save"})) {
                setDialogMode(modeIndex.get() == 0 ? DialogMode.OPEN : DialogMode.SAVE);
                changed = true;
            }
            l.popItemWidth();
            l.addVerticalSpacing(getSmallPadding());

            float gap = ImGui.getStyle().getItemSpacingX();
            float buttonWidth = Math.max((availableWidth - gap) / 2.0f, l.toPixels(80.0f));
            if (ImGui.button("Browse...", buttonWidth, 0)) {
                openFileDialog();
                changed = true;
            }
            ImGui.sameLine();
            if (ImGui.button("Clear", buttonWidth, 0)) {
                setSelectedPath("");
                changed = true;
            }

            l.addVerticalSpacing(getMediumPadding());
            return changed;
        });
    }

    private void openFileDialog() {
        Path initialDir = resolveInitialDirectory();
        boolean save = dialogMode == DialogMode.SAVE;
        FileDialogManager.showFileDialog(
            save ? "Select Save Path" : "Select File",
            initialDir,
            normalizeString(filter),
            save,
            normalizeString(defaultExtension),
            selected -> {
                if (selected != null) {
                    setSelectedPath(selected.toString());
                }
            }
        );
    }

    private Path resolveInitialDirectory() {
        String raw = normalizeString(initialDirectory);
        if (raw.isBlank()) {
            raw = ".";
        }
        try {
            return Path.of(raw).toAbsolutePath().normalize();
        } catch (Exception ignored) {
            return Path.of(".").toAbsolutePath().normalize();
        }
    }

    private void updateOutput() {
        String path = normalizeString(selectedPath).trim();
        Path parsed = parsePath(path);

        outputValues.put(OUTPUT_PATH_ID, path);
        outputValues.put(OUTPUT_TEXT_ID, path);
        outputValues.put(OUTPUT_DIRECTORY_ID, parsed != null && parsed.getParent() != null ? parsed.getParent().toString() : "");
        outputValues.put(OUTPUT_FILENAME_ID, parsed != null && parsed.getFileName() != null ? parsed.getFileName().toString() : "");
        outputValues.put(OUTPUT_EXTENSION_ID, getExtension(parsed));
        outputValues.put(OUTPUT_HAS_VALUE_ID, !path.isBlank());
        syncOutputPorts();
    }

    private @Nullable Path parsePath(String path) {
        if (path.isBlank()) {
            return null;
        }
        try {
            return Path.of(path).toAbsolutePath().normalize();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String getExtension(@Nullable Path path) {
        if (path == null || path.getFileName() == null) {
            return "";
        }
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    private String normalizeString(String value) {
        return value != null ? value : "";
    }

    public String getSelectedPath() {
        return selectedPath;
    }

    public void setSelectedPath(String selectedPath) {
        String normalized = normalizeString(selectedPath);
        if (!normalized.equals(this.selectedPath)) {
            this.selectedPath = normalized;
            updateOutput();
            markDirty();
        }
    }

    public DialogMode getDialogMode() {
        return dialogMode;
    }

    public void setDialogMode(DialogMode dialogMode) {
        DialogMode resolved = dialogMode != null ? dialogMode : DialogMode.OPEN;
        if (resolved != this.dialogMode) {
            this.dialogMode = resolved;
            markDirty();
        }
    }

    public String getInitialDirectory() {
        return initialDirectory;
    }

    public void setInitialDirectory(String initialDirectory) {
        String normalized = normalizeString(initialDirectory);
        if (!normalized.equals(this.initialDirectory)) {
            this.initialDirectory = normalized;
            markDirty();
        }
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        String normalized = normalizeString(filter);
        if (!normalized.equals(this.filter)) {
            this.filter = normalized;
            markDirty();
        }
    }

    public String getDefaultExtension() {
        return defaultExtension;
    }

    public void setDefaultExtension(String defaultExtension) {
        String normalized = normalizeString(defaultExtension);
        if (!normalized.equals(this.defaultExtension)) {
            this.defaultExtension = normalized;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("selectedPath", selectedPath);
        state.put("dialogMode", dialogMode.name());
        state.put("initialDirectory", initialDirectory);
        state.put("filter", filter);
        state.put("defaultExtension", defaultExtension);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("selectedPath") instanceof String value) {
            selectedPath = value;
        }
        if (map.get("dialogMode") instanceof String value) {
            try {
                dialogMode = DialogMode.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                dialogMode = DialogMode.OPEN;
            }
        }
        if (map.get("initialDirectory") instanceof String value) {
            initialDirectory = value;
        }
        if (map.get("filter") instanceof String value) {
            filter = value;
        }
        if (map.get("defaultExtension") instanceof String value) {
            defaultExtension = value;
        }
        updateOutput();
        invalidateCache();
        markDirty();
    }
}
