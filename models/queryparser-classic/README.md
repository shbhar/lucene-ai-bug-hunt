# Formal models and scripts: queryparser-classic

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- MultiFieldQueryParser under AND treats clause i of every field's analyzed BooleanQuery as term position i and requires each group, so fields whose analyzers tokenize the same text differently lose (and sometimes gain wrong) matches (released since 6.2.0, default settings since 7.0.0), and since #16446 (unreleased) graph-phrase alternatives such as a multi-word synonym are required together (bug LFV-319; [repro](../../repro/LFV-319/), [bug page](../../bugs/LFV-319.html))
- Classic QueryParser with the default splitOnWhitespace=false: since #16446 an AND/OR that directly follows a token the analyzer drops (such as a stopword) binds to the whole preceding run of plain terms instead of the last term, so typing the stopword changes which documents match (unreleased regression on main and branch_10_6) (bug LFV-284; [repro](../../repro/LFV-284/), [bug page](../../bugs/LFV-284.html))

## Files

- [mfqp-position-groups-by-clause-index/model/mfqp_groups.py](mfqp-position-groups-by-clause-index/model/mfqp_groups.py) (used for LFV-319)
- [multiterm-group-absorbs-conjunction-after-stopword/model/addclause_sow.py](multiterm-group-absorbs-conjunction-after-stopword/model/addclause_sow.py) (used for LFV-284)
- [multiterm-group-absorbs-conjunction-after-stopword/model/addclause_sow_nostop.py](multiterm-group-absorbs-conjunction-after-stopword/model/addclause_sow_nostop.py) (used for LFV-284)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
