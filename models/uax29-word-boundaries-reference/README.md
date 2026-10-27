# Formal models and scripts: uax29-word-boundaries-reference

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- StandardTokenizer (StandardAnalyzer's tokenizer) and UAX29URLEmailTokenizer misapply the UAX #29 Hebrew quote rules WB7a-WB7c next to other word characters: a Hebrew letter + ASCII apostrophe swallows a following digit or underscore (BET'2 is one term), an ASCII apostrophe after a Hebrew letter that ends a mid-word chain is dropped (HE.SHIN' -&gt; HE.SHIN), and a word with an ASCII quotation mark between Hebrew letters is split when it also contains a mid-word '.', ':' or quote (ALEF.TSADI"LAMED, TAV"ALEF.BET) (bug LFV-734; [repro](../../repro/LFV-734/), [bug page](../../bugs/LFV-734.html))

## Files

- [jflex-word-rule-hebrew-quote-pieces-not-chained/model/tools/classify.py](jflex-word-rule-hebrew-quote-pieces-not-chained/model/tools/classify.py) (used for LFV-734)
- [jflex-word-rule-hebrew-quote-pieces-not-chained/model/tools/minimal.py](jflex-word-rule-hebrew-quote-pieces-not-chained/model/tools/minimal.py) (used for LFV-734)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
