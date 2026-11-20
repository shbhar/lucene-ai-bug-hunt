# Formal models and scripts: unified-highlighter-cross-value-phrase-passage-end

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- UnifiedHighlighter from the default builder can print its internal value separator U+0000 inside a snippet of a multi-valued field, when a phrase match that runs from the end of one stored value into the next opens the passage (run: values 'one bravo three' and 'four bravo six', position gap 3, phrase "three four" with slop 3: 'one bravo &lt;b&gt;three\u0000four&lt;/b&gt;') (finding LFV-1561; [repro](../../repro/LFV-1561/))

## Files

- [uh-weight-matches-cross-value-match-opens-passage-past-value-end/model/smt/passage_bounds.py](uh-weight-matches-cross-value-match-opens-passage-past-value-end/model/smt/passage_bounds.py) (used for LFV-1561)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
