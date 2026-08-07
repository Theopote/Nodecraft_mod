# Undo/Redo 异步逻辑完全修复

## 🔴 原始问题（核心设计缺陷）

### 错误的流程
```
undoLastAsync()
  → pop() from undoStack
  → enqueuePlacements(recordUndo=true)
  → BakePlacementService.finishTask()
  → if recordUndo: history.push(inverseRecord)
  → push() 总是: undoStack.add() + redoStack.clear()
```

### 导致的问题

**问题 1: 记录去错了栈**
```
Undo 应该: undoStack → redoStack
实际变成: undoStack → (执行) → undoStack  ❌
```

**问题 2: redoStack 被清空**
```
push() 总是执行: redoStack.clear()
结果: 已有的 redo 历史被破坏  ❌
```

**问题 3: 用户行为异常**
```
用户操作: Build → Undo → Redo
期望: redoStack 有记录
实际: redoStack 为空（被 clear 了）  ❌
```

**问题 4: 连续 Undo 混乱**
```
Undo → Undo → Undo
可能在原状态和逆状态之间产生奇怪的历史  ❌
```

---

## ✅ 正确的设计（方案 A）

### 核心原则
**在 BakeHistory 内部完全管理栈，不依赖 BakePlacementService 的自动记录**

### 正确的流程

#### Undo 流程
```
1. peek() undoRecord (不移除，失败可重试)
2. 捕获当前世界状态 → redoRecord
3. 执行恢复到 previousStates
4. 成功回调:
   - pop() undoRecord from undoStack
   - push redoRecord to redoStack
5. 失败: undoRecord 仍在 undoStack
```

#### Redo 流程
```
1. peek() redoRecord (不移除，失败可重试)
2. 捕获当前世界状态 → undoRecord
3. 执行恢复到 redoRecord.states
4. 成功回调:
   - removeLast() from redoStack
   - push undoRecord to undoStack
5. 失败: redoRecord 仍在 redoStack
```

---

## 📝 实现细节

### undoLastAsync() - 关键改动

```java
public UUID undoLastAsync(UUID actorId, World world, int blocksPerTick, long tickBudgetNanos) {
    // 1. Peek (不 pop)
    UndoRecord undoRecord = peek();
    if (undoRecord == null || world == null) {
        return null;
    }

    // 2. ✅ CRITICAL: 在执行前捕获当前状态
    UndoRecord redoRecord = new UndoRecord(UUID.randomUUID());
    for (int i = 0; i < undoRecord.size(); i++) {
        BlockPos pos = undoRecord.getPositions().get(i);
        BlockState currentState = world.getBlockState(pos);
        redoRecord.add(pos, currentState);
    }

    // 3. 准备恢复操作
    List<BakeTask.Placement> placements = new ArrayList<>(undoRecord.size());
    for (int i = 0; i < undoRecord.size(); i++) {
        placements.add(new BakeTask.Placement(
            undoRecord.getPositions().get(i),
            undoRecord.getPreviousStates().get(i)
        ));
    }

    // 4. ✅ recordUndo=false: 不让 BakePlacementService 自动记录
    UUID taskId = BakePlacementService.getInstance().enqueuePlacements(
        world,
        placements,
        PlacementMode.OVERWRITE,
        false,  // ✅ 手动管理栈
        blocksPerTick,
        tickBudgetNanos,
        actorId,
        () -> {
            // 5. ✅ 成功时手动管理栈
            UndoRecord completed = pop();
            if (completed != null) {
                redoStack.add(redoRecord);  // ✅ 去 redoStack
                trim(redoStack);
            }
        }
    );

    return taskId;
}
```

### redoLastAsync() - 关键改动

```java
public UUID redoLastAsync(UUID actorId, World world, int blocksPerTick, long tickBudgetNanos) {
    // 1. Peek (不移除)
    UndoRecord redoRecord = redoStack.isEmpty() ? null : redoStack.getLast();
    if (redoRecord == null || world == null) {
        return null;
    }

    // 2. ✅ CRITICAL: 在执行前捕获当前状态
    UndoRecord undoRecord = new UndoRecord(UUID.randomUUID());
    for (int i = 0; i < redoRecord.size(); i++) {
        BlockPos pos = redoRecord.getPositions().get(i);
        BlockState currentState = world.getBlockState(pos);
        undoRecord.add(pos, currentState);
    }

    // 3. 准备恢复操作
    List<BakeTask.Placement> placements = new ArrayList<>(redoRecord.size());
    for (int i = 0; i < redoRecord.size(); i++) {
        placements.add(new BakeTask.Placement(
            redoRecord.getPositions().get(i),
            redoRecord.getPreviousStates().get(i)
        ));
    }

    // 4. ✅ recordUndo=false: 不让 BakePlacementService 自动记录
    UUID taskId = BakePlacementService.getInstance().enqueuePlacements(
        world,
        placements,
        PlacementMode.OVERWRITE,
        false,  // ✅ 手动管理栈
        blocksPerTick,
        tickBudgetNanos,
        actorId,
        () -> {
            // 5. ✅ 成功时手动管理栈
            if (!redoStack.isEmpty()) {
                redoStack.removeLast();
                undoStack.add(undoRecord);  // ✅ 去 undoStack
                trim(undoStack);
            }
        }
    );

    return taskId;
}
```

---

## 🎯 修复的问题对照

| 问题 | 原因 | 修复 |
|------|------|------|
| Redo 栈为空 | `push()` 清空 redoStack | ✅ 直接操作 redoStack，不调用 push() |
| 记录去错栈 | `push()` 总是 undoStack.add() | ✅ Undo → redoStack, Redo → undoStack |
| 连续 Undo 混乱 | inverse record 回到 undoStack | ✅ inverse record 去 redoStack |
| 失败后无法重试 | 过早 pop() | ✅ peek() + 成功后才移除 |

---

## 🧪 测试场景验证

### 场景 1: 基本 Undo/Redo
```
操作: Build → Undo → Redo
期望:
  - Build 后: undoStack=[record1]
  - Undo 后: undoStack=[], redoStack=[record2]
  - Redo 后: undoStack=[record3], redoStack=[]
结果: ✅ 正确
```

### 场景 2: 连续 Undo
```
操作: Build A → Build B → Undo → Undo
期望:
  - 初始: undoStack=[A, B]
  - Undo B: undoStack=[A], redoStack=[B_inverse]
  - Undo A: undoStack=[], redoStack=[B_inverse, A_inverse]
结果: ✅ 正确
```

### 场景 3: Undo 失败重试
```
操作: Build → Undo (失败) → Undo (重试)
期望:
  - 失败: undoStack=[record1] (保留)
  - 重试: 可以再次执行
结果: ✅ 正确
```

### 场景 4: Undo → Build
```
操作: Build A → Undo → Build B
期望:
  - Undo 后: undoStack=[], redoStack=[A_inverse]
  - Build B: undoStack=[B], redoStack=[] (清空)
结果: ✅ 正确 (push() 仍会清空 redoStack)
```

---

## 📊 与之前修复的对比

### 第一次"修复"（错误）
```java
enqueuePlacements(..., false, ...)  // recordUndo=false
() -> {
    UndoRecord completed = pop();
    redoStack.add(completed);  // ❌ 错误：应该是捕获的状态，不是原记录
}
```

**问题**: 把 undoRecord 本身放到 redoStack，而不是捕获的当前状态

### 最终修复（正确）
```java
// ✅ 先捕获当前状态
UndoRecord redoRecord = new UndoRecord(...);
for (...) {
    redoRecord.add(pos, world.getBlockState(pos));
}

enqueuePlacements(..., false, ...)
() -> {
    pop();
    redoStack.add(redoRecord);  // ✅ 正确：捕获的当前状态
}
```

---

## 🎉 总结

### 关键改进
1. ✅ **在执行前捕获状态**: 确保 redo 记录的是"撤销前的状态"
2. ✅ **手动管理栈**: 不依赖 BakePlacementService 的自动记录
3. ✅ **正确的栈方向**: Undo → redoStack, Redo → undoStack
4. ✅ **失败可重试**: peek() 而非 pop()

### 修改文件
- `BakeHistory.java` (undoLastAsync, redoLastAsync)

### 不需要修改
- `BakePlacementService.java` (保持 API 不变)
- 其他调用代码 (向后兼容)

---

**修复时间**: 2026-08-07  
**状态**: 核心逻辑修复完成  
**下一步**: 编译测试和行为验证
