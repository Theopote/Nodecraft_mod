package com.nodecraft.gui.dialogs;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.nodecraft.core.NodeCraft;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

/**
 * 文件对话框管理器，提供通用的文件选择功能
 */
public class FileDialogManager {
    // 对话框状态
    private static boolean showOpenDialog = false;
    private static boolean showSaveDialog = false;
    private static String dialogTitle = "";
    private static Path currentDirectory = Paths.get("").toAbsolutePath();
    private static String fileFilter = "";
    private static String defaultExtension = "";
    private static final ImString filename = new ImString(256);
    private static Path selectedPath = null;
    private static Consumer<Path> callback = null;
    
    // 文件列表
    private static List<String> fileList = new ArrayList<>();
    private static List<String> dirList = new ArrayList<>();
    
    /**
     * 显示打开文件对话框
     * 
     * @param title 对话框标题
     * @param initialDir 初始目录
     * @param filter 文件过滤器描述，格式如 "图像文件 (*.jpg;*.png)"
     * @return 选择的文件路径，如果用户取消则返回null
     */
    public static Path showOpenFileDialog(String title, Path initialDir, String filter) {
        try {
            NodeCraft.LOGGER.info("请求打开文件对话框: 标题={}, 初始目录={}", title, initialDir);
            
            // 保存对话框状态
            dialogTitle = title;
            currentDirectory = initialDir;
            fileFilter = filter;
            filename.set("");
            
            // 更新文件列表
            updateFileList();
            
            // 标记对话框需要显示
            showOpenDialog = true;
            
            // 注意：这个方法现在只是设置了状态，并不会阻塞等待用户选择
            // 实际的文件选择将由主渲染循环处理
            // 由于立即返回，这里总是返回null
            return null;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("显示打开文件对话框时出错: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 显示保存文件对话框
     * 
     * @param title 对话框标题
     * @param initialDir 初始目录
     * @param filter 文件过滤器描述，格式如 "图像文件 (*.jpg;*.png)"
     * @param defaultExt 默认扩展名，如 ".jpg"
     * @return 选择的文件路径，如果用户取消则返回null
     */
    public static Path showSaveFileDialog(String title, Path initialDir, String filter, String defaultExt) {
        try {
            NodeCraft.LOGGER.info("请求保存文件对话框: 标题={}, 初始目录={}", title, initialDir);
            
            // 保存对话框状态
            dialogTitle = title;
            currentDirectory = initialDir;
            fileFilter = filter;
            defaultExtension = defaultExt;
            filename.set("");
            
            // 更新文件列表
            updateFileList();
            
            // 标记对话框需要显示
            showSaveDialog = true;
            
            // 同样，这里只是设置状态，不阻塞等待
            return null;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("显示保存文件对话框时出错: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 使用回调函数的文件对话框（推荐使用此方法）
     * @param title 对话框标题
     * @param initialDir 初始目录
     * @param filter 文件过滤器
     * @param isSaveDialog 是否为保存对话框
     * @param defaultExt 默认扩展名（仅用于保存对话框）
     * @param resultCallback 结果回调函数
     */
    public static void showFileDialog(String title, Path initialDir, String filter, 
                                     boolean isSaveDialog, String defaultExt,
                                     Consumer<Path> resultCallback) {
        try {
            // 保存回调函数
            callback = resultCallback;
            
            // 调用相应的对话框方法
            if (isSaveDialog) {
                showSaveFileDialog(title, initialDir, filter, defaultExt);
            } else {
                showOpenFileDialog(title, initialDir, filter);
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("显示文件对话框时出错: {}", e.getMessage(), e);
            // 出错时调用回调，传入null
            if (callback != null) {
                callback.accept(null);
            }
        }
    }
    
    /**
     * 更新当前目录的文件列表
     */
    private static void updateFileList() {
        fileList.clear();
        dirList.clear();
        
        try {
            File dir = currentDirectory.toFile();
            if (!dir.exists() || !dir.isDirectory()) {
                currentDirectory = Paths.get("").toAbsolutePath();
                dir = currentDirectory.toFile();
            }
            
            // 添加上级目录选项
            dirList.add("..");
            
            // 列出目录和文件
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        dirList.add(file.getName());
                    } else {
                        // 如果有过滤器，检查文件是否匹配
                        if (matchesFilter(file.getName())) {
                            fileList.add(file.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("更新文件列表时出错: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 检查文件名是否符合过滤器
     */
    private static boolean matchesFilter(String filename) {
        if (fileFilter == null || fileFilter.isEmpty()) {
            return true;
        }
        
        try {
            // 提取过滤器中的扩展名
            int startPos = fileFilter.indexOf("(*.");
            int endPos = fileFilter.indexOf(")", startPos);
            
            if (startPos >= 0 && endPos > startPos) {
                String extPart = fileFilter.substring(startPos + 3, endPos);
                String[] extensions = extPart.split(";\\*");
                
                for (String ext : extensions) {
                    if (filename.toLowerCase().endsWith(ext.toLowerCase())) {
                        return true;
                    }
                }
                
                return false;
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.warn("过滤文件时出错: {}", e.getMessage());
        }
        
        return true;
    }
    
    /**
     * 渲染文件对话框
     * 应该在每一帧调用此方法来渲染和处理文件对话框
     */
    public static void renderFileDialogs() {
        if (showOpenDialog) {
            renderOpenDialog();
        }
        
        if (showSaveDialog) {
            renderSaveDialog();
        }
    }
    
    /**
     * 渲染打开文件对话框
     */
    private static void renderOpenDialog() {
        int windowFlags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoDocking |
                         ImGuiWindowFlags.NoResize | ImGuiWindowFlags.AlwaysAutoResize;
        
        ImGui.setNextWindowSize(400, 300, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowPos(ImGui.getIO().getDisplaySizeX() / 2, ImGui.getIO().getDisplaySizeY() / 2, 
                              ImGuiCond.FirstUseEver, 0.5f, 0.5f);
        
        ImBoolean isOpen = new ImBoolean(true);
        if (ImGui.begin(dialogTitle + "###FileOpenDialog", isOpen, windowFlags)) {
            List<String> dirSnapshot = new ArrayList<>(dirList);
            List<String> fileSnapshot = new ArrayList<>(fileList);

            // 显示当前路径
            ImGui.text("当前目录: " + currentDirectory.toString());
            ImGui.separator();
            
            // 渲染目录列表
            if (ImGui.beginListBox("###Directories", 380, 100)) {
                for (String dir : dirSnapshot) {
                    if (ImGui.selectable("[目录] " + dir)) {
                        if ("..".equals(dir)) {
                            // 切换到上级目录
                            Path parent = currentDirectory.getParent();
                            if (parent != null) {
                                currentDirectory = parent;
                                updateFileList();
                            }
                        } else {
                            // 切换到选中的目录
                            currentDirectory = currentDirectory.resolve(dir);
                            updateFileList();
                        }
                    }
                }
                ImGui.endListBox();
            }
            
            // 渲染文件列表
            if (ImGui.beginListBox("###Files", 380, 100)) {
                for (String file : fileSnapshot) {
                    if (ImGui.selectable(file, false)) {
                        filename.set(file);
                    }
                }
                ImGui.endListBox();
            }
            
            // 文件名输入框
            ImGui.text("文件名:");
            ImGui.sameLine();
            ImGui.inputText("###FileName", filename);
            
            // 按钮行
            ImGui.separator();
            if (ImGui.button("打开", 120, 0)) {
                if (!filename.get().isEmpty()) {
                    Path selected = currentDirectory.resolve(filename.get());
                    if (Files.exists(selected) && !Files.isDirectory(selected)) {
                        selectedPath = selected;
                        showOpenDialog = false;
                        
                        // 调用回调函数
                        if (callback != null) {
                            callback.accept(selectedPath);
                            callback = null;
                        }
                    }
                }
            }
            
            ImGui.sameLine();
            if (ImGui.button("取消", 120, 0)) {
                showOpenDialog = false;
                
                // 调用回调函数，传入null表示取消
                if (callback != null) {
                    callback.accept(null);
                    callback = null;
                }
            }
        }
        ImGui.end();
        
        // 检查窗口是否被关闭（点击右上角X按钮）
        if (!isOpen.get() && showOpenDialog) {
            showOpenDialog = false;
            // 调用回调函数，传入null表示取消
            if (callback != null) {
                callback.accept(null);
                callback = null;
            }
        }
    }
    
    /**
     * 渲染保存文件对话框
     */
    private static void renderSaveDialog() {
        int windowFlags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoDocking |
                         ImGuiWindowFlags.NoResize | ImGuiWindowFlags.AlwaysAutoResize;
        
        ImGui.setNextWindowSize(400, 300, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowPos(ImGui.getIO().getDisplaySizeX() / 2, ImGui.getIO().getDisplaySizeY() / 2, 
                              ImGuiCond.FirstUseEver, 0.5f, 0.5f);
        
        ImBoolean isOpen = new ImBoolean(true);
        if (ImGui.begin(dialogTitle + "###FileSaveDialog", isOpen, windowFlags)) {
            List<String> dirSnapshot = new ArrayList<>(dirList);
            List<String> fileSnapshot = new ArrayList<>(fileList);

            // 显示当前路径
            ImGui.text("当前目录: " + currentDirectory.toString());
            ImGui.separator();
            
            // 渲染目录列表
            if (ImGui.beginListBox("###Directories", 380, 100)) {
                for (String dir : dirSnapshot) {
                    if (ImGui.selectable("[目录] " + dir)) {
                        if ("..".equals(dir)) {
                            // 切换到上级目录
                            Path parent = currentDirectory.getParent();
                            if (parent != null) {
                                currentDirectory = parent;
                                updateFileList();
                            }
                        } else {
                            // 切换到选中的目录
                            currentDirectory = currentDirectory.resolve(dir);
                            updateFileList();
                        }
                    }
                }
                ImGui.endListBox();
            }
            
            // 渲染文件列表
            if (ImGui.beginListBox("###Files", 380, 100)) {
                for (String file : fileSnapshot) {
                    if (ImGui.selectable(file, false)) {
                        filename.set(file);
                    }
                }
                ImGui.endListBox();
            }
            
            // 文件名输入框
            ImGui.text("文件名:");
            ImGui.sameLine();
            ImGui.inputText("###FileName", filename);
            
            // 按钮行
            ImGui.separator();
            if (ImGui.button("保存", 120, 0)) {
                if (!filename.get().isEmpty()) {
                    String name = filename.get();
                    
                    // 检查扩展名
                    if (defaultExtension != null && !defaultExtension.isEmpty() && 
                        !name.toLowerCase().endsWith(defaultExtension.toLowerCase())) {
                        name += defaultExtension;
                    }

                    selectedPath = currentDirectory.resolve(name);
                    showSaveDialog = false;
                    
                    // 调用回调函数
                    if (callback != null) {
                        callback.accept(selectedPath);
                        callback = null;
                    }
                }
            }
            
            ImGui.sameLine();
            if (ImGui.button("取消", 120, 0)) {
                showSaveDialog = false;
                
                // 调用回调函数，传入null表示取消
                if (callback != null) {
                    callback.accept(null);
                    callback = null;
                }
            }
        }
        ImGui.end();
        
        // 检查窗口是否被关闭（点击右上角X按钮）
        if (!isOpen.get() && showSaveDialog) {
            showSaveDialog = false;
            // 调用回调函数，传入null表示取消
            if (callback != null) {
                callback.accept(null);
                callback = null;
            }
        }
    }
} 