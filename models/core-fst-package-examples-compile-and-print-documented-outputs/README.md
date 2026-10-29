# Formal models and scripts: core-fst-package-examples-compile-and-print-documented-outputs

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- The org.apache.lucene.util.fst package overview's code examples do not compile on any release since 9.0.0: they call Util.getByOutput (removed in 9.0.0), the public FSTCompiler(INPUT_TYPE, Outputs) constructor (removed in 9.9.0) and the Util.Result fields input/output (private record components since 10.0.0) (documentation defect) (bug LFV-1027; [repro](../../repro/LFV-1027/))

## Files

- [fst-package-overview-examples-call-removed-apis/model/check_verbatim.py](fst-package-overview-examples-call-removed-apis/model/check_verbatim.py) (used for LFV-1027)
- [fst-package-overview-examples-call-removed-apis/model/extract_blocks.py](fst-package-overview-examples-call-removed-apis/model/extract_blocks.py) (used for LFV-1027)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
