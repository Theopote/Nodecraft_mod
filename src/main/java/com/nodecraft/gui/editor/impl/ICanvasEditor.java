package com.nodecraft.gui.editor.impl;

import java.util.Map;
import java.util.UUID;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.api.INode;
import imgui.ImVec2;
import org.jetbrains.annotations.Nullable;

/**
 * 瀹氫箟涓庣敾甯冪紪杈戝櫒浜や簰鎵€闇€鐨勬帴鍙?
 */
public interface ICanvasEditor {
    enum NodeAlignmentAction {
        ALIGN_LEFT,
        ALIGN_CENTER,
        DISTRIBUTE_HORIZONTAL
    }

    float getCanvasZoom();
    float getCanvasOffsetX();
    float getCanvasOffsetY();
    NodeGraph getCurrentGraph();
    UUID getSelectedNodeId();
    void setSelectedNodeId(UUID nodeId);
    boolean connectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId);
    boolean disconnectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId);
    
    // 娣诲姞 ImGuiNodeIO 闇€瑕佺殑鏂规硶
    Map<UUID, NodePosition> getNodePositions();
    NodePosition getNodePosition(UUID nodeId);
    void setCurrentGraph(NodeGraph graph);
    void setNodePositions(Map<UUID, NodePosition> positions);
    
    // 娣诲姞 ImGuiNodeMenus 鍙兘闇€瑕佺殑鏂规硶
    boolean isShowGrid();
    void setShowGrid(boolean showGrid);
    void setCanvasZoom(float zoom);
    void setCanvasOffset(float x, float y);
    void clearNodePositions();
    void clearSelectedNodes();
    void removeSelectedNode(UUID nodeId);
    void removeNodePosition(UUID nodeId);
    UUID getNodeIdUnderMouse(float mouseX, float mouseY);
    void close();
    INode addNode(String nodeTypeId, float x, float y);
    
    /**
     * 鏍规嵁鑺傜偣绫诲瀷ID銆佹寚瀹歎UID鍜屽垵濮嬬姸鎬佸垱寤哄苟娣诲姞涓€涓妭鐐瑰埌鍥句腑銆?
     * 姝ゆ柟娉曚富瑕佺敤浜庡巻鍙茶褰曠殑鎾ら攢/閲嶅仛鍔熻兘锛屼互鎭㈠鐗瑰畾ID鍜岀姸鎬佺殑鑺傜偣銆?
     *
     * @param nodeTypeId 鑺傜偣鐨勭被鍨婭D銆?
     * @param oldNodeId 鑺傜偣鐨勬棫UUID锛堜粎鐢ㄤ簬鍘嗗彶璁板綍鍐呴儴鏄犲皠锛屾柊鍒涘缓鐨勮妭鐐逛細鏈夋柊UUID锛夛紝濡傛灉涓簄ull锛屽皢鑷姩鐢熸垚鏂扮殑UUID銆?
     * @param x 鑺傜偣鐨勫垵濮嬩笘鐣孹鍧愭爣銆?
     * @param y 鑺傜偣鐨勫垵濮嬩笘鐣孻鍧愭爣銆?
     * @param nodeState 鑺傜偣鐨勫垵濮嬬姸鎬佹暟鎹紝灏嗛€氳繃 setNodeState 搴旂敤銆傚鏋滀负null锛屽垯浣跨敤鑺傜偣榛樿鐘舵€併€?
     * @return 鏂板垱寤烘垨鎭㈠鐨勮妭鐐瑰疄渚嬶紝濡傛灉澶辫触鍒欒繑鍥瀗ull銆?
     */
    INode addNodeWithState(String nodeTypeId, @Nullable UUID oldNodeId, float x, float y, @Nullable Object nodeState);
    
    java.util.Set<UUID> getSelectedNodeIds();
    void setCanvasView(float zoom, float offsetX, float offsetY);
    void pasteNodesAtPosition(float x, float y);
    
    /**
     * 鑾峰彇鑺傜偣浜や簰缁勪欢
     * @return 鑺傜偣浜や簰缁勪欢瀹炰緥
     */
    ImGuiNodeInteraction getInteraction();
    
    /**
     * 鑾峰彇绔彛灞忓箷浣嶇疆鏄犲皠
     * @return 绔彛灞忓箷浣嶇疆鏄犲皠
     */
    Map<UUID, Map<String, ImVec2>> getPortScreenPositions();
    
    /**
     * 鑾峰彇鑺傜偣IO缁勪欢
     * @return 鑺傜偣IO缁勪欢瀹炰緥
     */
    ImGuiNodeIO getNodeIO();
    
    /**
     * 鑾峰彇鍘嗗彶璁板綍缁勪欢
     * @return 鍘嗗彶璁板綍缁勪欢瀹炰緥
     */
    ImGuiNodeHistory getHistory();
    
    /**
     * 鑾峰彇鍓创鏉跨粍浠?
     * @return 鍓创鏉跨粍浠跺疄渚?
     */
    ImGuiNodeClipboard getClipboard();
    
    /**
     * 鎾ら攢鎿嶄綔
     * @return 鏄惁鎴愬姛鎾ら攢
     */
    boolean undo();
    
    /**
     * 閲嶅仛鎿嶄綔
     * @return 鏄惁鎴愬姛閲嶅仛
     */
    boolean redo();
    
    /**
     * 澶嶅埗閫変腑鐨勮妭鐐?
     * @return 鏄惁鎴愬姛澶嶅埗
     */
    boolean copySelectedNodes();
    
    /**
     * 鍓垏閫変腑鐨勮妭鐐?
     * @return 鏄惁鎴愬姛鍓垏
     */
    boolean cutSelectedNodes();
    
    /**
     * 鍦ㄦ寚瀹氫綅缃矘璐磋妭鐐?
     * @param x 绮樿创浣嶇疆鐨刋鍧愭爣
     * @param y 绮樿创浣嶇疆鐨刌鍧愭爣
     * @return 鏄惁鎴愬姛绮樿创
     */
    boolean pasteNodesAt(float x, float y);
    
    /**
     * 鍒犻櫎閫変腑鐨勮妭鐐?
     * @return 鏄惁鎴愬姛鍒犻櫎
     */
    boolean deleteSelectedNodes();
    
    /**
     * 妫€鏌ユ槸鍚︽湁鏈繚瀛樼殑鏇存敼
     * @return 鏄惁鏈夋湭淇濆瓨鐨勬洿鏀?
     */
    boolean hasUnsavedChanges();
    
    /**
     * 澶嶅埗閫変腑鐨勮妭鐐?
     * @return 鏄惁鎴愬姛澶嶅埗
     */
    boolean duplicateSelectedNode();

    boolean alignNodes(java.util.Set<UUID> nodeIds, NodeAlignmentAction action);

    // === 鑺傜偣棰滆壊绠＄悊鏂规硶 ===

    /**
     * 璁剧疆鑺傜偣鐨勮嚜瀹氫箟棰滆壊
     * @param nodeId 鑺傜偣ID
     * @param color 棰滆壊鍊硷紙ImGui鏍煎紡鐨勬暣鏁伴鑹诧級
     */
    void setNodeCustomColor(UUID nodeId, int color);

    /**
     * 鑾峰彇鑺傜偣鐨勮嚜瀹氫箟棰滆壊
     * @param nodeId 鑺傜偣ID
     * @return 鑷畾涔夐鑹诧紝濡傛灉娌℃湁璁剧疆鍒欒繑鍥瀗ull
     */
    Integer getNodeCustomColor(UUID nodeId);

    /**
     * 绉婚櫎鑺傜偣鐨勮嚜瀹氫箟棰滆壊
     * @param nodeId 鑺傜偣ID
     */
    void removeNodeCustomColor(UUID nodeId);

    /**
     * 妫€鏌ヨ妭鐐规槸鍚︽湁鑷畾涔夐鑹?
     * @param nodeId 鑺傜偣ID
     * @return 鏄惁鏈夎嚜瀹氫箟棰滆壊
     */
    boolean hasNodeCustomColor(UUID nodeId);

    // === 鑺傜偣鐘舵€佺鐞嗘柟娉?===

    /**
     * 鍒囨崲鑺傜偣鐨勭鐢ㄧ姸鎬?
     * @param nodeId 鑺傜偣ID
     * @return 鍒囨崲鍚庣殑鐘舵€侊紙true=绂佺敤锛宖alse=鍚敤锛?
     */
    boolean toggleNodeDisabled(UUID nodeId);

    /**
     * 璁剧疆鑺傜偣鐨勭鐢ㄧ姸鎬?
     * @param nodeId 鑺傜偣ID
     * @param disabled 鏄惁绂佺敤
     */
    void setNodeDisabled(UUID nodeId, boolean disabled);

    /**
     * 妫€鏌ヨ妭鐐规槸鍚﹁绂佺敤
     * @param nodeId 鑺傜偣ID
     * @return 鏄惁琚鐢?
     */
    boolean isNodeDisabled(UUID nodeId);

    /**
     * 鍒囨崲鑺傜偣鐨勫彲瑙佹€х姸鎬?
     * @param nodeId 鑺傜偣ID
     * @return 鍒囨崲鍚庣殑鐘舵€侊紙true=鍙锛宖alse=闅愯棌锛?
     */
    boolean toggleNodeVisible(UUID nodeId);

    /**
     * 璁剧疆鑺傜偣鐨勫彲瑙佹€х姸鎬?
     * @param nodeId 鑺傜偣ID
     * @param visible 鏄惁鍙
     */
    void setNodeVisible(UUID nodeId, boolean visible);

    /**
     * 妫€鏌ヨ妭鐐规槸鍚﹀彲瑙?
     * @param nodeId 鑺傜偣ID
     * @return 鏄惁鍙
     */
    boolean isNodeVisible(UUID nodeId);
} 
