# Formal models and scripts: spellchecker-candidate-budget-int-overflow

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- DirectSpellChecker.suggestSimilar computes its candidate budget numSug * maxInspections in int: with setMaxInspections(Integer.MAX_VALUE) ('inspect everything') and any even numSug the budget wraps to -numSug, so the call throws NullPointerException or silently returns no suggestions (finding LFV-986; [repro](../../repro/LFV-986/), [finding page](../../bugs/LFV-986.html))
- SpellChecker.suggestSimilar computes its hit budget 10 * numSug in int, so numSug = Integer.MAX_VALUE ('all suggestions'), 214748365 and every other count whose product wraps to &lt;= 0 throw IllegalArgumentException 'numHits must be &gt; 0' instead of returning the suggestions, and counts whose product wraps to a small positive value silently return fewer suggestions (finding LFV-996; [repro](../../repro/LFV-996/), [finding page](../../bugs/LFV-996.html))

## Files

- [directspellchecker-inspection-budget-int-overflow/model/budget_smt.py](directspellchecker-inspection-budget-int-overflow/model/budget_smt.py) (used for LFV-986)
- [spellchecker-maxhits-int-overflow-and-queue-presize/model/budget_smt.py](spellchecker-maxhits-int-overflow-and-queue-presize/model/budget_smt.py) (used for LFV-996)
- [spellchecker-maxhits-int-overflow-and-queue-presize/model/wrap_intervals.py](spellchecker-maxhits-int-overflow-and-queue-presize/model/wrap_intervals.py) (used for LFV-996)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
