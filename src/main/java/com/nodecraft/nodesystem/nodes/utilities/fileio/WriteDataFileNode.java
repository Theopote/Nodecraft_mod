package com.nodecraft.nodesystem.nodes.utilities.fileio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Write Data File 节点: 将数据写入结构化文件（JSON、CSV等）
 */
@NodeInfo(
    id = "utilities.fileio.write_data_file",
    displayName = "写入数据文件",
    description = "将数据写入结构化文件（JSON、CSV等）",
    category = "utilities.fileio"
)
public class WriteDataFileNode extends BaseNode {

    private static final Path ALLOWED_BASE_DIRECTORY = Paths.get("").toAbsolutePath().normalize();

    // --- 节点属性 ---
    private String filePath = ""; // 文件路径
    private String encoding = "UTF-8"; // 文件编码
    private String fileType = "CSV"; // 文件类型 (CSV, JSON)
    private String csvDelimiter = ","; // CSV 分隔符
    private boolean includeHeader = true; // 是否包含CSV表头
    private boolean appendMode = false; // 是否追加模式
    private boolean prettyJson = true; // 是否格式化JSON
    private boolean createDirectory = true; // 是否创建目录
    private String description = "将结构化数据写入文件（CSV、JSON 等）";
    
    // --- 输入端口 IDs ---
    private static final String INPUT_FILE_PATH_ID = "input_file_path";
    private static final String INPUT_DATA_ID = "input_data";
    private static final String INPUT_HEADERS_ID = "input_headers";
    private static final String INPUT_ENCODING_ID = "input_encoding";
    private static final String INPUT_FILE_TYPE_ID = "input_file_type";
    private static final String INPUT_CSV_DELIMITER_ID = "input_csv_delimiter";
    private static final String INPUT_APPEND_ID = "input_append";
    private static final String INPUT_TRIGGER_ID = "input_trigger";
    
    // --- 输出端口 IDs ---
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_BYTES_WRITTEN_ID = "output_bytes_written";
    private static final String OUTPUT_FILE_PATH_ID = "output_file_path";
    private static final String OUTPUT_ERROR_ID = "output_error";

    // --- 文件类型常量 ---
    private static final String FILE_TYPE_CSV = "CSV";
    private static final String FILE_TYPE_JSON = "JSON";
    
    /**
     * 构造一个新的写入数据文件节点
     */
    public WriteDataFileNode() {
        super(UUID.randomUUID(), "utilities.fileio.write_data_file");
        
        // 创建输入端口
        addInputPort(new BasePort(INPUT_FILE_PATH_ID, "File Path", 
                "要写入的数据文件路径", NodeDataType.FILE_PATH, this));
        
        addInputPort(new BasePort(INPUT_DATA_ID, "Data", 
                "要写入的结构化数据（列表或对象）", NodeDataType.ANY, this));
        
        addInputPort(new BasePort(INPUT_HEADERS_ID, "Headers", 
                "CSV表头或要包含的JSON字段（可选）", NodeDataType.LIST, this));
        
        addInputPort(new BasePort(INPUT_ENCODING_ID, "Encoding", 
                "文件编码（如UTF-8）", NodeDataType.STRING, this));
        
        addInputPort(new BasePort(INPUT_FILE_TYPE_ID, "File Type", 
                "文件类型（CSV, JSON）", NodeDataType.STRING, this));
        
        addInputPort(new BasePort(INPUT_CSV_DELIMITER_ID, "CSV Delimiter", 
                "CSV分隔符（默认为逗号）", NodeDataType.STRING, this));
        
        addInputPort(new BasePort(INPUT_APPEND_ID, "Append", 
                "是否追加到现有文件", NodeDataType.BOOLEAN, this));
        
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", 
                "触发写入操作", NodeDataType.ANY, this));
        
        // 创建输出端口
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", 
                "是否成功写入文件", NodeDataType.BOOLEAN, this));
        
        addOutputPort(new BasePort(OUTPUT_BYTES_WRITTEN_ID, "Bytes Written", 
                "写入的字节数", NodeDataType.INTEGER, this));
        
        addOutputPort(new BasePort(OUTPUT_FILE_PATH_ID, "File Path", 
                "写入的文件路径", NodeDataType.FILE_PATH, this));
        
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", 
                "写入失败时的错误信息", NodeDataType.STRING, this));
    }
    
    /**
     * 节点的计算逻辑
     */
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 初始化输出值
        boolean success = false;
        long bytesWritten = 0;
        String savedPath = "";
        String errorMessage = "";
        
        // 获取输入值
        Object filePathObj = inputValues.get(INPUT_FILE_PATH_ID);
        Object dataObj = inputValues.get(INPUT_DATA_ID);
        Object headersObj = inputValues.get(INPUT_HEADERS_ID);
        Object encodingObj = inputValues.get(INPUT_ENCODING_ID);
        Object fileTypeObj = inputValues.get(INPUT_FILE_TYPE_ID);
        Object csvDelimiterObj = inputValues.get(INPUT_CSV_DELIMITER_ID);
        Object appendObj = inputValues.get(INPUT_APPEND_ID);
        Object triggerObj = inputValues.get(INPUT_TRIGGER_ID);
        
        // 确定文件路径
        String filePathToWrite = this.filePath;
        if (filePathObj instanceof String && !((String) filePathObj).isEmpty()) {
            filePathToWrite = (String) filePathObj;
        }
        
        // 确定编码
        String encodingToUse = this.encoding;
        if (encodingObj instanceof String && !((String) encodingObj).isEmpty()) {
            encodingToUse = (String) encodingObj;
        }
        
        // 确定文件类型
        String fileTypeToUse = this.fileType;
        if (fileTypeObj instanceof String && !((String) fileTypeObj).isEmpty()) {
            fileTypeToUse = ((String) fileTypeObj).toUpperCase();
        }
        
        // 确定CSV分隔符
        String delimiterToUse = this.csvDelimiter;
        if (csvDelimiterObj instanceof String && !((String) csvDelimiterObj).isEmpty()) {
            delimiterToUse = (String) csvDelimiterObj;
        }
        
        // 确定是否追加模式
        boolean shouldAppend = this.appendMode;
        if (appendObj instanceof Boolean) {
            shouldAppend = (Boolean) appendObj;
        }
        
        // 检查是否应该写入文件
        boolean shouldWrite = triggerObj != null && dataObj != null;
        
        // 如果路径为空，或者未触发写入，则直接返回
        if (filePathToWrite.isEmpty() || !shouldWrite) {
            // 设置未成功状态和错误消息
            outputValues.put(OUTPUT_SUCCESS_ID, false);
            outputValues.put(OUTPUT_BYTES_WRITTEN_ID, 0);
            outputValues.put(OUTPUT_FILE_PATH_ID, "");
            outputValues.put(OUTPUT_ERROR_ID, "未写入：无文件路径、无数据或未触发");
            return;
        }
        
        // 提取表头
        List<String> headers = new ArrayList<>();
        if (headersObj instanceof List) {
            List<?> headersList = (List<?>) headersObj;
            for (Object header : headersList) {
                headers.add(header != null ? header.toString() : "");
            }
        }
        
        try {
            Path path = resolveSafeOutputPath(filePathToWrite);
            Path parent = path.getParent();
            
            // 如果需要，创建父目录
            if (parent != null && !Files.exists(parent) && createDirectory) {
                Files.createDirectories(parent);
            }
            
            // 确定字符集
            Charset charset;
            try {
                charset = Charset.forName(encodingToUse);
            } catch (Exception e) {
                // 如果指定的编码无效，使用UTF-8
                charset = StandardCharsets.UTF_8;
            }
            
            // 根据文件类型处理数据
            String contentToWrite = "";
            
            if (FILE_TYPE_CSV.equalsIgnoreCase(fileTypeToUse)) {
                // 转换为CSV格式
                contentToWrite = convertToCSV(dataObj, headers, delimiterToUse, includeHeader);
            } else if (FILE_TYPE_JSON.equalsIgnoreCase(fileTypeToUse)) {
                // 转换为JSON格式
                contentToWrite = convertToJSON(dataObj, prettyJson);
            } else {
                errorMessage = "不支持的文件类型: " + fileTypeToUse;
                outputValues.put(OUTPUT_SUCCESS_ID, false);
                outputValues.put(OUTPUT_BYTES_WRITTEN_ID, 0);
                outputValues.put(OUTPUT_FILE_PATH_ID, "");
                outputValues.put(OUTPUT_ERROR_ID, errorMessage);
                return;
            }
            
            // 写入文件
            if (shouldAppend && Files.exists(path)) {
                // 追加模式
                bytesWritten = Files.write(path, contentToWrite.getBytes(charset), 
                        StandardOpenOption.APPEND, StandardOpenOption.CREATE).toFile().length();
            } else {
                // 覆盖模式
                bytesWritten = Files.write(path, contentToWrite.getBytes(charset)).toFile().length();
            }
            
            success = true;
            savedPath = path.toString();
        } catch (Exception e) {
            success = false;
            errorMessage = "写入文件时出错: " + e.getMessage();
        }
        
        // 设置输出值
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_BYTES_WRITTEN_ID, (int)bytesWritten);
        outputValues.put(OUTPUT_FILE_PATH_ID, savedPath);
        outputValues.put(OUTPUT_ERROR_ID, errorMessage);
    }
    
    /**
     * 将数据转换为CSV格式
     */
    private String convertToCSV(Object data, List<String> headers, String delimiter, boolean includeHeader) {
        StringBuilder sb = new StringBuilder();
        
        // 如果数据是列表
        if (data instanceof List) {
            List<?> dataList = (List<?>) data;
            
            // 添加表头
            if (includeHeader && !headers.isEmpty()) {
                for (int i = 0; i < headers.size(); i++) {
                    if (i > 0) {
                        sb.append(delimiter);
                    }
                    sb.append(escapeCSV(headers.get(i), delimiter));
                }
                sb.append("\n");
            }
            
            // 添加数据行
            for (Object row : dataList) {
                if (row instanceof Map) {
                    // 如果行是Map，使用表头获取值
                    Map<?, ?> rowMap = (Map<?, ?>) row;
                    if (!headers.isEmpty()) {
                        // 使用指定的表头顺序
                        for (int i = 0; i < headers.size(); i++) {
                            if (i > 0) {
                                sb.append(delimiter);
                            }
                            Object value = rowMap.get(headers.get(i));
                            sb.append(escapeCSV(value != null ? value.toString() : "", delimiter));
                        }
                    } else {
                        // 如果没有表头，使用map的值
                        boolean first = true;
                        for (Object value : rowMap.values()) {
                            if (!first) {
                                sb.append(delimiter);
                            }
                            sb.append(escapeCSV(value != null ? value.toString() : "", delimiter));
                            first = false;
                        }
                    }
                } else if (row instanceof List) {
                    // 如果行是List，直接使用列表值
                    List<?> rowList = (List<?>) row;
                    for (int i = 0; i < rowList.size(); i++) {
                        if (i > 0) {
                            sb.append(delimiter);
                        }
                        Object value = rowList.get(i);
                        sb.append(escapeCSV(value != null ? value.toString() : "", delimiter));
                    }
                } else if (row instanceof Object[]) {
                    // 如果行是数组，直接使用数组值
                    Object[] rowArray = (Object[]) row;
                    for (int i = 0; i < rowArray.length; i++) {
                        if (i > 0) {
                            sb.append(delimiter);
                        }
                        Object value = rowArray[i];
                        sb.append(escapeCSV(value != null ? value.toString() : "", delimiter));
                    }
                } else {
                    // 简单值，作为单列
                    sb.append(escapeCSV(row != null ? row.toString() : "", delimiter));
                }
                sb.append("\n");
            }
        } else if (data instanceof Map) {
            // 单个对象Map
            Map<?, ?> dataMap = (Map<?, ?>) data;
            
            // 添加表头（键）
            if (includeHeader) {
                boolean first = true;
                
                // 使用指定的表头顺序（如果有）
                if (!headers.isEmpty()) {
                    for (String header : headers) {
                        if (!first) {
                            sb.append(delimiter);
                        }
                        sb.append(escapeCSV(header, delimiter));
                        first = false;
                    }
                } else {
                    // 否则使用Map的键
                    for (Object key : dataMap.keySet()) {
                        if (!first) {
                            sb.append(delimiter);
                        }
                        sb.append(escapeCSV(key != null ? key.toString() : "", delimiter));
                        first = false;
                    }
                }
                sb.append("\n");
            }
            
            // 添加数据行
            boolean first = true;
            if (!headers.isEmpty()) {
                // 使用指定的表头顺序
                for (String header : headers) {
                    if (!first) {
                        sb.append(delimiter);
                    }
                    Object value = dataMap.get(header);
                    sb.append(escapeCSV(value != null ? value.toString() : "", delimiter));
                    first = false;
                }
            } else {
                // 否则使用Map的值
                for (Object value : dataMap.values()) {
                    if (!first) {
                        sb.append(delimiter);
                    }
                    sb.append(escapeCSV(value != null ? value.toString() : "", delimiter));
                    first = false;
                }
            }
            sb.append("\n");
        } else {
            // 单个简单值
            sb.append(escapeCSV(data != null ? data.toString() : "", delimiter));
        }
        
        return sb.toString();
    }

    private Path resolveSafeOutputPath(String rawPath) {
        Path candidate = Paths.get(rawPath);
        Path normalized = candidate.isAbsolute()
                ? candidate.toAbsolutePath().normalize()
                : ALLOWED_BASE_DIRECTORY.resolve(candidate).normalize();

        if (!normalized.startsWith(ALLOWED_BASE_DIRECTORY)) {
            throw new IllegalArgumentException("文件路径超出允许目录: " + rawPath);
        }

        return normalized;
    }
    
    /**
     * 转义CSV字段
     */
    private String escapeCSV(String value, String delimiter) {
        boolean needsQuotes = value.contains(delimiter) || value.contains("\"") || value.contains("\n");
        
        if (needsQuotes) {
            // 替换双引号为两个双引号（CSV转义规则）
            value = value.replace("\"", "\"\"");
            // 用双引号包围整个字段
            return "\"" + value + "\"";
        }
        
        return value;
    }
    
    /**
     * 将数据转换为JSON格式
     */
    private String convertToJSON(Object data, boolean pretty) {
        Gson gson;
        if (pretty) {
            gson = new GsonBuilder().setPrettyPrinting().create();
        } else {
            gson = new Gson();
        }
        
        return gson.toJson(data);
    }
    
    /**
     * 浏览选择保存位置
     * 实际实现中此方法应该由UI层调用，打开文件保存对话框
     */
    public boolean browseForSaveLocation() {
        // 实际实现在UI层
        return false;
    }
    
    // --- Getters/Setters for Properties ---
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
        markDirty();
    }
    
    public String getEncoding() {
        return encoding;
    }
    
    public void setEncoding(String encoding) {
        this.encoding = encoding;
        markDirty();
    }
    
    public String getFileType() {
        return fileType;
    }
    
    public void setFileType(String fileType) {
        this.fileType = fileType;
        markDirty();
    }
    
    public String getCsvDelimiter() {
        return csvDelimiter;
    }
    
    public void setCsvDelimiter(String csvDelimiter) {
        this.csvDelimiter = csvDelimiter;
        markDirty();
    }
    
    public boolean isIncludeHeader() {
        return includeHeader;
    }
    
    public void setIncludeHeader(boolean includeHeader) {
        this.includeHeader = includeHeader;
        markDirty();
    }
    
    public boolean isAppendMode() {
        return appendMode;
    }
    
    public void setAppendMode(boolean appendMode) {
        this.appendMode = appendMode;
        markDirty();
    }
    
    public boolean isPrettyJson() {
        return prettyJson;
    }
    
    public void setPrettyJson(boolean prettyJson) {
        this.prettyJson = prettyJson;
        markDirty();
    }
    
    public boolean isCreateDirectory() {
        return createDirectory;
    }
    
    public void setCreateDirectory(boolean createDirectory) {
        this.createDirectory = createDirectory;
        markDirty();
    }
    
    /**
     * 保存节点状态
     */
    @Override
    public @Nullable Object getNodeState() {
        Object[] state = new Object[8];
        state[0] = filePath;
        state[1] = encoding;
        state[2] = fileType;
        state[3] = csvDelimiter;
        state[4] = includeHeader;
        state[5] = appendMode;
        state[6] = prettyJson;
        state[7] = createDirectory;
        return state;
    }
    
    /**
     * 恢复节点状态
     */
    @Override
    public void setNodeState(@Nullable Object state) {
        if (state instanceof Object[]) {
            Object[] objState = (Object[]) state;
            if (objState.length >= 8) {
                if (objState[0] instanceof String) {
                    filePath = (String) objState[0];
                }
                if (objState[1] instanceof String) {
                    encoding = (String) objState[1];
                }
                if (objState[2] instanceof String) {
                    fileType = (String) objState[2];
                }
                if (objState[3] instanceof String) {
                    csvDelimiter = (String) objState[3];
                }
                if (objState[4] instanceof Boolean) {
                    includeHeader = (Boolean) objState[4];
                }
                if (objState[5] instanceof Boolean) {
                    appendMode = (Boolean) objState[5];
                }
                if (objState[6] instanceof Boolean) {
                    prettyJson = (Boolean) objState[6];
                }
                if (objState[7] instanceof Boolean) {
                    createDirectory = (Boolean) objState[7];
                }
            }
        }
    }

    @Override
    public String getDescription() {
        return this.description;
    }
} 