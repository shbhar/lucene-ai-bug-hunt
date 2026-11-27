# Formal models and scripts: synonymmap-builder-key-order-unpaired-surrogate

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- On Lucene 3.4.0 to 10.6.0 SynonymMap.Builder.build() can hand its keys to the FST out of order when one key holds an unpaired surrogate: the synonym rule of another, well-formed word can then never fire in the map that build() returns, with no error when assertions are off, or build() can throw ArrayIndexOutOfBoundsException (run: five one-word rules behind a filter that cuts every word to its first UTF-16 unit, the stock TruncateTokenFilter from 4.8.0 on, fail with and without assertions on each of the 136 releases that were run; a second set of five rules throws with assertions off on each of the 55 releases from 8.4.0; branch_11x and main, both unreleased, no longer have that code) (finding LFV-1571; [repro](../../repro/LFV-1571/), [finding page](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1571.html))

## Files

- [model/order/order_model.py](model/order/order_model.py) (used for LFV-1571)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
