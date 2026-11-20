# Formal models and scripts: icutokenizer-japanese-word-segmentation

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- The default ICUTokenizer can split the same Japanese text differently later in a JVM than at first (run: 'コン ーング゠' gives [コン, ーング゠], then [コン, ーン, グ゠]), because the ICU4J library it depends on picks the break engine for a run of dictionary characters from state that its word iterators share and that earlier text changes; known upstream (#9269 / LUCENE-8222, ICU-13669) (finding LFV-1566; [repro](../../repro/LFV-1566/))

## Files

- [icutokenizer-tokens-depend-on-jvm-wide-icu4j-break-engine-state/model/engine_selection_model.py](icutokenizer-tokens-depend-on-jvm-wide-icu4j-break-engine-state/model/engine_selection_model.py) (used for LFV-1566)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
