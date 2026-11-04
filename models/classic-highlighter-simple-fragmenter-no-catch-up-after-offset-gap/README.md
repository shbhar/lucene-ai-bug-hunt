# Formal models and scripts: classic-highlighter-simple-fragmenter-no-catch-up-after-offset-gap

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- Classic Highlighter: SimpleFragmenter and SimpleSpanFragmenter move their fragment boundary only one fragment size per break, so after a long token or a long gap between tokens (a run of spaces, stripped markup) every following word becomes its own fragment and getBestFragment can return just the highlighted word (every release since 1.9) (finding LFV-1367; [repro](../../repro/LFV-1367/))

## Files

- [simplefragmenter-counter-no-catch-up-after-offset-gap/model/fragmenter_counter.py](simplefragmenter-counter-no-catch-up-after-offset-gap/model/fragmenter_counter.py) (used for LFV-1367)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
