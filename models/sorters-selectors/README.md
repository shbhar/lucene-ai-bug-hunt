# Formal models and scripts: sorters-selectors

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Sorter.heapChild overflows int, so IntroSorter's heap-sort fallback compares a negative slot outside [from, to) when 2*to - from &gt; 2^31 (ArrayIndexOutOfBoundsException from ArrayUtil.introSort on a legal array slice) (bug LFV-372; [repro](../../repro/LFV-372/))

## Files

- [sorter-heapchild-int-overflow/model/index_overflow.py](sorter-heapchild-int-overflow/model/index_overflow.py) (used for LFV-372)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
