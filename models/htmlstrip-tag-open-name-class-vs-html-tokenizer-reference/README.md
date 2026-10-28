# Formal models and scripts: htmlstrip-tag-open-name-class-vs-html-tokenizer-reference

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- HTMLStripCharFilter drops an unclosed '&lt;name' or '&lt;/name' when the input ends right after the name, but outputs the same text when any character follows it, so 'a &lt; b' indexes only 'a' (bug LFV-878; [repro](../../repro/LFV-878/), [bug page](../../bugs/LFV-878.html))

## Files

- [htmlstrip-unclosed-tag-name-at-end-of-input-dropped/model/eof_policy_model.py](htmlstrip-unclosed-tag-name-at-end-of-input-dropped/model/eof_policy_model.py) (used for LFV-878)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
