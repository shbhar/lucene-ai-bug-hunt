# Formal models and scripts: expressions-compiler

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- Expressions: !, &amp;&amp;, || and ?: keep only the low 32 bits of a bitwise/shift result or hex/octal literal, so a non-zero value such as 1 &lt;&lt; 32 or 0x100000000 counts as false (finding LFV-181; [repro](../../repro/LFV-181/), [finding page](../../bugs/LFV-181.html))

## Files

- [model/smt/truth_conversions.py](model/smt/truth_conversions.py) (used for LFV-181)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
