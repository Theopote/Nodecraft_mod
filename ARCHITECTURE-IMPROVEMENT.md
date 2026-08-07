# 架构改进：BakeOperationKind - 清晰的历史语义

## 🎯 问题：让 BakeTask 猜历史语义是错误的

### 之前的设计缺陷

```java
// 之前：用一个 boolean 代表三种完全不同的语义
enqueuePlacements(..., boolean recordUndo, ...)

finishTask(task) {
    if (recordUndo) {
        history.push(inverse);  // ❌ 总是去 undoStack + 清空 redoStack
    }
}
```

**问题**：
- `recordUndo=true` 无法区分是 **APPLY**、**UNDO** 还是 **REDO**
- 这三种操作在历史系统中的含义**完全不同**
- `push()` 总是清空 redoStack，破坏 Redo 历史

---

## ✅ 新设计：显式的操作语义

### 1. 引入 BakeOperationKind 枚举

```java
public enum BakeOperationKind {
    APPLY,  // 普通 Bake：inverse → undoStack (清空 redoStack)
    UNDO,   // 撤销操作：inverse → redoStack
    REDO,   // 重做操作：inverse → undoStack (不清空 redoStack)
    NONE    // 无历史记录：临时/预览操作
}
```

### 2. BakeTask 携带操作语义

```java
public class BakeTask {
    private final BakeOperationKind operationKind;  // NEW
    
    public BakeTask(..., BakeOperationKind operationKind, ...) {
        this.operationKind = operationKind != null ? operationKind : BakeOperationKind.APPLY;
    }
    
    public BakeOperationKind getOperationKind() {
        return operationKind;
    }
}
```

### 3. BakeHistory 提供明确的 API

```java
public class BakeHistory {
    // 普通 Bake：清空 redoStack
    public void push(UndoRecord record) {
        undoStack.add(record);
        redoStack.clear();  // 标准 undo/redo 语义
        trim(undoStack);
    }
    
    // Redo 操作：不清空 redoStack
    public void pushUndo(UndoRecord record) {
        undoStack.add(record);
        trim(undoStack);
    }
    
    // Undo 操作：去 redoStack
    public void pushRedo(UndoRecord record) {
        redoStack.add(record);
        trim(redoStack);
    }
}
```

### 4. BakePlacementService 智能路由

```java
private void finishTask(BakeTask task) {
    if (!task.getUndoRecords().isEmpty()) {
        BakeHistory.UndoRecord record = buildInverseRecord(task);
        BakeHistory history = getHistory(task.getActorId());
        
        // ✅ 根据操作类型智能路由
        switch (task.getOperationKind()) {
            case APPLY -> history.push(record);      // → undoStack (清空 redo)
            case UNDO  -> history.pushRedo(record);  // → redoStack
            case REDO  -> history.pushUndo(record);  // → undoStack (保留 redo)
            case NONE  -> { /* 不记录 */ }
        }
    }
}
```

### 5. 使用新 API 的 Undo/Redo

```java
public UUID undoLastAsync(...) {
    UndoRecord undoRecord = peek();
    
    // ✅ 明确指定这是 UNDO 操作
    UUID taskId = BakePlacementService.getInstance().enqueuePlacements(
        world,
        placements,
        PlacementMode.OVERWRITE,
        true,  // 记录 inverse
        BakeOperationKind.UNDO,  // ← 语义清晰
        blocksPerTick,
        tickBudgetNanos,
        actorId,
        () -> pop()  // 成功后移除
    );
    
    return taskId;
}

public UUID redoLastAsync(...) {
    UndoRecord redoRecord = redoStack.getLast();
    
    // ✅ 明确指定这是 REDO 操作
    UUID taskId = BakePlacementService.getInstance().enqueuePlacements(
        world,
        placements,
        PlacementMode.OVERWRITE,
        true,  // 记录 inverse
        BakeOperationKind.REDO,  // ← 语义清晰
        blocksPerTick,
        tickBudgetNanos,
        actorId,
        () -> redoStack.removeLast()  // 成功后移除
    );
    
    return taskId;
}
```

---

## 📊 改进对比

### 语义清晰度

| 方面 | 之前 | 现在 |
|------|------|------|
| 操作类型 | `boolean recordUndo` | `BakeOperationKind` 枚举 |
| 语义表达 | ❌ 模糊 | ✅ 明确 |
| 可扩展性 | ❌ 只有 true/false | ✅ 可添加新类型 |
| 代码可读性 | ❌ 需要猜测 | ✅ 一目了然 |

### 历史路由

| 操作 | 之前 | 现在 |
|------|------|------|
| APPLY | → undoStack (清空 redo) | ✅ → undoStack (清空 redo) |
| UNDO | ❌ → undoStack (破坏 redo) | ✅ → redoStack |
| REDO | ❌ → undoStack (破坏 redo) | ✅ → undoStack (保留 redo) |

### 代码复杂度

```java
// 之前：在 BakeHistory 中手动捕获状态
undoLastAsync() {
    UndoRecord redoRecord = captureCurrentState();  // ❌ 手动管理
    enqueuePlacements(..., false, ...);
    callback: { 
        pop();
        redoStack.add(redoRecord);  // ❌ 手动路由
    }
}

// 现在：让服务自动处理
undoLastAsync() {
    enqueuePlacements(..., true, BakeOperationKind.UNDO, ...);  // ✅ 自动处理
    callback: { pop(); }  // ✅ 只需移除
}
```

---

## 🎯 架构优势

### 1. 单一职责
- **BakeTask**: 携带操作语义
- **BakePlacementService**: 执行操作 + 记录 inverse
- **BakeHistory**: 管理栈

### 2. 开闭原则
添加新的操作类型只需：
```java
enum BakeOperationKind {
    APPLY, UNDO, REDO, NONE,
    PREVIEW,  // 新类型：不记录，但显示在 UI
    MERGE     // 新类型：合并到上一个 undo record
}
```

### 3. 依赖倒置
- BakeHistory 不需要知道 "谁调用了我"
- BakePlacementService 根据 `operationKind` 决定路由
- 调用方明确表达意图

### 4. 测试友好
```java
@Test
void testUndoRoutesToRedoStack() {
    BakeTask task = new BakeTask(..., BakeOperationKind.UNDO, ...);
    service.finishTask(task);
    // ✅ 明确验证：inverse 应该在 redoStack
    assertTrue(history.getRedoStack().contains(inverseRecord));
}
```

---

## 🔄 迁移影响

### 修改的文件
1. **BakeOperationKind.java** - 新文件
2. **BakeTask.java** - 添加 `operationKind` 字段
3. **BakeHistory.java** - 添加 `pushUndo()` 和 `pushRedo()`
4. **BakePlacementService.java** - `finishTask()` 使用 switch

### 向后兼容
```java
// 旧代码仍然可用（默认为 APPLY）
public UUID enqueuePlacements(..., boolean recordUndo, ...) {
    return enqueuePlacements(..., recordUndo, BakeOperationKind.APPLY, ...);
}
```

### 不需要修改
- 所有现有调用方（默认为 APPLY）
- 测试代码（除非测试 Undo/Redo）

---

## 🎉 总结

### 核心改进
1. ✅ **语义清晰**: `BakeOperationKind` 明确表达操作意图
2. ✅ **正确路由**: `finishTask()` 根据语义智能路由
3. ✅ **简化逻辑**: Undo/Redo 不再需要手动捕获和路由
4. ✅ **架构优雅**: 单一职责 + 开闭原则

### 修复的问题
- ❌ recordUndo 语义模糊 → ✅ BakeOperationKind 清晰
- ❌ 手动捕获状态 → ✅ 服务自动处理
- ❌ 手动路由栈 → ✅ switch 智能路由
- ❌ Redo 历史被破坏 → ✅ 正确保留

### 下一步
1. 编译测试
2. 单元测试验证路由逻辑
3. 集成测试 Undo/Redo 场景

---

**设计时间**: 2026-08-07  
**状态**: 架构改进完成  
**影响**: 4 个文件，向后兼容
