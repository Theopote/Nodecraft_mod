# Node Language v2 — List Core

**Status: PASSED / FROZEN** (Graph **V126**; V23 remains historical for collection-wide rules)

Strict index & null contract remediation for seven core `math.list.*` nodes:
exact `Integer` list indices, Create List connected-null preservation,
and documented negative-index / non-destructive edit semantics.

Related: [`node-language-v2-list-collection.md`](./node-language-v2-list-collection.md),
[`node-language-v1-list-collection.md`](./node-language-v1-list-collection.md),
[`node-language-v2-sequence.md`](./node-language-v2-sequence.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Nodes in scope (7)

Create List, Get Item, Set Item, Insert Item, Remove Item, Sub List, List Length.

## Strict INTEGER index contract

All list index ports resolve via `ListIndexResolver`:

| Port state | Behavior |
|------------|----------|
| Undriven | node-specific default (Sub List Start=0, End=size) or fail (Get/Set/Insert/Remove) |
| Driven + exact `Integer` | use value |
| Driven + wrong type / null | **fail** (no `Number.intValue()` coercion) |

## Create List null semantics

| Port state | Behavior |
|------------|----------|
| Undriven | skip (omit from output) |
| Driven + value | append value |
| Driven + null | append **null** (preserves slot order) |

Generic `LIST` allows null elements. Undriven skip compacts; connected null does not shift later elements.
**Create List** is a Generic/Advanced packer (picker `order=80`); it does not produce typed `LIST<T>`.

## Collection language

| Type | Elements |
|------|----------|
| `ANY` | arbitrary nullable single value |
| `LIST` | heterogeneous; null allowed |
| `LIST<T>` (`T` constrained) | homogeneous; non-null; runtime-compatible with `T` |
| `DATA_TREE<T>` | homogeneous; non-null; unique canonical paths; bounded |
| `TREE_PATH` | immutable non-negative integer path; depth-capped |

Set / Insert on constrained `T`: connected null or kind mismatch → `Valid=false`, empty list, `Error`.

## Set Item fail-closed

Invalid index / type / value → `output_list=[]`, `output_valid=false`, `output_error` set.
Port id is `output_valid` (stamp-only graphs drop former `output_success` wires).
Insert / Remove expose `output_valid` + `output_error`. Get Item keeps **Found**.

## Negative index rules

After strict type validation, negative indices count from end (`-1` = last element):

| Node | Negative index | Wrap property |
|------|----------------|---------------|
| Get Item | optional (`allowNegativeIndex`) | optional (`wrapIndex`) |
| Set Item | always normalized from end | optional (`wrapIndex`) |
| Insert Item | normalized; `-1` inserts **before** last element | none (strict bounds) |
| Remove Item (index mode) | normalized from end | none |
| Sub List | Start/End normalized from end | none |

Insert Item valid range after normalization: `0 ≤ index ≤ size` (inclusive upper bound allows append-at-end).

## Non-destructive list edits

Set / Insert / Remove / Sub List copy the input list container (`new ArrayList<>(inputList)`) before modifying.
The input list reference is never mutated. Element objects are **not** deep-copied (reference semantics preserved).

## Remove Item dual mode (unchanged, deferred split)

`useIndex` / `removeAllMatches` properties select index vs value removal.
Split into separate canonical nodes remains deferred (see v1 list doc §Deferred).

## List Length (unchanged in V126)

Non-list input currently yields `Length=0`. `output_valid` deferred to global list-language follow-up.

## Graph migration (V125→V126)

Identity migration — runtime-only index/null semantics; no wire remaps.
