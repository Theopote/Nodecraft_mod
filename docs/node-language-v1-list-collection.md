# List / Collection / Data Tree language v1

Freeze against Graph **V22**.

## Principles

- **`LIST`** = flat heterogeneous / generic collection only
- **Typed `*_LIST`** = element-kind-safe collections (`POINT_LIST`, `DOUBLE_LIST`, `BOOLEAN_LIST`, …)
- **Nested `List<List<?>>` is not a tree** — structure uses **`DATA_TREE`**
- **`LIST` ↔ `DATA_TREE`** is explicit only (Graft / Flatten / Partition / Group)
- No string→number coercion on typed numeric list pipes
- No Property that changes collection element type or output shape

## Typed scalar lists (V22)

| Type | Kind | Typical producers / consumers |
|------|------|-------------------------------|
| `DOUBLE_LIST` | `DOUBLE` | Number Sequence, Number Series, List Statistics, Map Numbers |
| `BOOLEAN_LIST` | `BOOLEAN` | Dispatch List mask |

## Numeric list nodes

| Node | Ports |
|------|-------|
| Number Sequence (`math.sequence.range`) | Start/End/Step → `DOUBLE_LIST` |
| Number Series (`math.sequence.series`) | Start/Step/Count → `DOUBLE_LIST` (no `useIntegerType`) |
| List Statistics | `DOUBLE_LIST` → Min/Max/Sum/Average/Median/Count/Valid |
| Map Numbers (`math.list.map_numbers`) | `DOUBLE_LIST` → `DOUBLE_LIST` (+ Count/Valid) |
| Dispatch List | List + Mask `BOOLEAN_LIST` → True/False `LIST` |

## Create / Repeat

| Node | Semantics |
|------|-----------|
| Create List | Dynamic `ANY` inputs → `LIST` (no Allow Mixed Types) |
| Repeat Item | Item `ANY` + Count → `LIST`; list values repeat as **one element** (never tile) |

## Structure

| Node | Status |
|------|--------|
| Graft List / Flatten Tree / Partition List To Tree | KEEP |
| Group List | KEEP — emits `DATA_TREE` (+ Unique Keys / Group Count) |
| Chunk List / Combine Lists / Zip Lists / Transpose | **DELETED** (V22) |

## Graph migration (V21→V22)

- Removes Chunk / Combine / Zip / Transpose nodes
- Remaps `math.list.map_list` → `math.list.map_numbers`
- Drops Group List `output_groups` wires (port is now `output_tree`)
- Drops Map Numbers legacy `output_changed_count` wires
- Strips legacy state: `allowDifferentTypes`, `useIntegerType`, `ignoreNonNumeric`, `ignoreNulls`

## Deferred (P2)

- `INTEGER_LIST` / `STRING_LIST`
- `TREE_PATH` typed path
- Remaining generic list tools (Filter / Sort / …) second-pass polish
