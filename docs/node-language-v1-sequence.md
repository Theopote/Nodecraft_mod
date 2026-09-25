# Node Language v1 — Sequence

Freeze for `math.sequence.*` list producers.
Shared implementation: `com.nodecraft.nodesystem.math.SequenceOps`.
Graph schema: **V28** drops Number Series `output_sum` wires.

Related: [`node-language-v1-scalar-math.md`](./node-language-v1-scalar-math.md) (finite numeric),
[`node-language-v1-logic.md`](./node-language-v1-logic.md) (INTEGER = Integer),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Core rules

1. **Exact step** — no hidden epsilon (`1e-10`, `step * 0.001`).
2. **Finite DOUBLE_LIST** — never emit Infinity / NaN as list elements.
3. **Hard cap** — at most `GenerationLimits.MAX_LIST_ELEMENTS` elements.
4. **INTEGER Count** — only `Integer` (same as Logic Switch Index).

## Number Sequence (`math.sequence.range`)

Value-bounded: Start + End + Step → `DOUBLE_LIST`.

| Case | Result |
|------|--------|
| non-finite inputs | `[]` |
| `step == 0.0` | `[]` |
| `start == end` | `[start]` |
| ascending / descending match | index-based `start + i * step` while not past End |
| direction mismatch | `[]` |

Does **not** guarantee End is included unless a generated value lands on End.
Does **not** flip step or swap endpoints.

## Number Series (`math.sequence.series`)

Count-bounded: Start + Step + Count → `DOUBLE_LIST` only.

- Count: `Integer` → clamp; non-Integer → property default
- Stops before adding first non-finite value
- **Sum removed** — use `math.list.sum_numbers`

## Repeat Item (`math.sequence.repeat`)

| Port | Type |
|------|------|
| Item | `ANY` + `bindListElementType("T")` |
| Count | `INTEGER` (`Integer` only) |
| Result | `LIST` + `bindListType("T")` |
| Length | `INTEGER` (actual size after clamp) |

Lists used as Item are repeated as **one element** (never tiled).

## Sequence vs List

| Category | Role |
|----------|------|
| `math.sequence` | produce lists from parameters |
| `math.list` | operate on existing lists |

## Graph migration (V27→V28)

Drop wires from `math.sequence.series#output_sum`. No node deletion.
