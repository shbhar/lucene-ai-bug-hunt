# Formal models and scripts: study-automaton-verification

Tools: TLA+/TLC, Dafny.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Operations.isTotal returns true for a deterministic automaton that can reach a dead state, so an AutomatonQuery can match every term (bug LFV-049; [repro](../../repro/LFV-049/), [bug page](../../bugs/LFV-049.html))
- AutomatonTermsEnum throws ArrayIndexOutOfBoundsException for an NFA with a reachable dead state (bug LFV-055; [repro](../../repro/LFV-055/))
- UTF32ToUTF8 still accepts overlong UTF-8 for ranges that span into 3- and 4-byte code points (bug LFV-057; [repro](../../repro/LFV-057/), [bug page](../../bugs/LFV-057.html))
- Operations.getSingleton returns null for a single-string DFA with a dead state (bug LFV-075; [repro](../../repro/LFV-075/))
- Operations.topoSortStates misses self-loop cycles and returns an order instead of throwing (bug LFV-062; [repro](../../repro/LFV-062/))

## Files

- [dafny/Concat.dfy](dafny/Concat.dfy)
- [dafny/IsTotal.dfy](dafny/IsTotal.dfy)
- [dafny/Repeat.dfy](dafny/Repeat.dfy)
- [dafny/mutants/Concat_no_crossing.dfy](dafny/mutants/Concat_no_crossing.dfy)
- [dafny/mutants/Concat_no_eps_accept.dfy](dafny/mutants/Concat_no_eps_accept.dfy)
- [dafny/mutants/Repeat_no_group3.dfy](dafny/mutants/Repeat_no_group3.dfy)
- [dafny/mutants/Repeat_weak_shortcut.dfy](dafny/mutants/Repeat_weak_shortcut.dfy)
- [tla/TopoSort.tla](tla/TopoSort.tla)
- [tla/TopoSort_bug.cfg](tla/TopoSort_bug.cfg)
- [tla/TopoSort_bug4.cfg](tla/TopoSort_bug4.cfg)
- [tla/TopoSort_fix.cfg](tla/TopoSort_fix.cfg)
- [tla/TopoSort_fix4.cfg](tla/TopoSort_fix4.cfg)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).
- Dafny: `dafny verify <file>.dfy` (a failed obligation marks where the code's contract breaks).
