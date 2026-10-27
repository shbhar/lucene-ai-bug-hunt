# Formal models and scripts: study-fst-verification

Tools: Alloy, Dafny, Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- FSTMetadata.save always writes the current version, so re-saving a pre-9.0 BYTE2 FST breaks its lookups (bug LFV-052; [repro](../../repro/LFV-052/), [bug page](../../bugs/LFV-052.html))
- FSTCompiler silently writes a corrupt FST when a fixed-length-arc node would expand to 2^31 bytes or more (bug LFV-007; [repro](../../repro/LFV-007/), [bug page](../../bugs/LFV-007.html))
- Util.shortestPaths with topN=0 throws NoSuchElementException (bug LFV-070; [repro](../../repro/LFV-070/))
- FSTCompiler throws ArrayIndexOutOfBoundsException on BYTE4 nodes whose labels span 2^31-8 or more (bug LFV-067; [repro](../../repro/LFV-067/))

## Files

- [alloy/fst_arcs.als](alloy/fst_arcs.als)
- [bin/matrix_table.py](bin/matrix_table.py)
- [dafny/BitTable.dfy](dafny/BitTable.dfy)
- [dafny/mut/BitTable_mutant_prev.dfy](dafny/mut/BitTable_mutant_prev.dfy)
- [smt/da_decision.py](smt/da_decision.py)
- [smt/expand_overflow.py](smt/expand_overflow.py)
- [smt/scan_expand.py](smt/scan_expand.py)

## How to run

- Alloy 6: open the `.als` file and run its commands (an instance found for a bug assertion is the bug).
- Dafny: `dafny verify <file>.dfy` (a failed obligation marks where the code's contract breaks).
- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
