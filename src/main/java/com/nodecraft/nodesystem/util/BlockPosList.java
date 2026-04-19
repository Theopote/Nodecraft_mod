package com.nodecraft.nodesystem.util;

import net.minecraft.util.math.BlockPos;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * 方块坐标列表，用于存储和操作多个方块坐标
 */
public class BlockPosList implements Iterable<BlockPos> {
    
    private final List<BlockPos> positions;
    
    /**
     * 创建一个空的方块坐标列表
     */
    public BlockPosList() {
        this.positions = new ArrayList<>();
    }
    
    /**
     * 使用已有的方块坐标列表创建新的方块坐标列表
     * @param positions 方块坐标列表
     */
    public BlockPosList(Collection<BlockPos> positions) {
        this.positions = new ArrayList<>(positions);
    }
    
    /**
     * 添加方块坐标
     * @param pos 要添加的方块坐标
     * @return 此列表
     */
    public BlockPosList add(BlockPos pos) {
        positions.add(pos.toImmutable());
        return this;
    }
    
    /**
     * 添加多个方块坐标
     * @param positions 要添加的方块坐标集合
     * @return 此列表
     */
    public BlockPosList addAll(Collection<BlockPos> positions) {
        for (BlockPos pos : positions) {
            this.positions.add(pos.toImmutable());
        }
        return this;
    }
    
    /**
     * 移除方块坐标
     * @param pos 要移除的方块坐标
     * @return 如果列表包含该坐标并成功移除，返回true
     */
    public boolean remove(BlockPos pos) {
        return positions.remove(pos);
    }
    
    /**
     * 获取列表大小
     * @return 列表中的方块坐标数量
     */
    public int size() {
        return positions.size();
    }
    
    /**
     * 判断列表是否为空
     * @return 如果列表为空返回true
     */
    public boolean isEmpty() {
        return positions.isEmpty();
    }
    
    /**
     * 检查列表是否包含指定的方块坐标
     * @param pos 要检查的方块坐标
     * @return 如果列表包含该坐标返回true
     */
    public boolean contains(BlockPos pos) {
        return positions.contains(pos);
    }
    
    /**
     * 清空列表
     */
    public void clear() {
        positions.clear();
    }
    
    /**
     * 获取列表副本
     * @return 方块坐标列表的副本
     */
    public List<BlockPos> getPositions() {
        return new ArrayList<>(positions);
    }
    
    @Override
    public @NonNull Iterator<BlockPos> iterator() {
        return positions.iterator();
    }
    
    @Override
    public String toString() {
        return "BlockPosList{size=" + positions.size() + "}";
    }
} 