# Formal models and scripts: suggester-lookup-num-overflow-and-presize

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Document suggester: NRTSuggester computes topN = num * prefixPaths.size in int, so when the product passes Integer.MAX_VALUE it wraps and SuggestIndexSearcher.suggest can silently return no suggestions or a truncated list (10^6 contexts in a segment: num 2147 returns 2147 suggestions, num 2148 returns none) (bug LFV-999; [repro](../../repro/LFV-999/), [bug page](../../bugs/LFV-999.html))
- BlendedInfixSuggester.lookup computes num * numFactor in int, so a large num wraps: num = Integer.MAX_VALUE throws IllegalArgumentException 'numHits must be &gt; 0', some values throw NoSuchElementException, and specific values silently return fewer suggestions than exist (bug LFV-998; [repro](../../repro/LFV-998/))
- AnalyzingSuggester and FuzzySuggester compute the top-N queue depth num * maxAnalyzedPathsForOneInput in int, so with a synonym (token graph) analyzer a large num wraps it: lookup silently returns too few suggestions and skips higher-weighted ones, or throws NoSuchElementException, and with assertions on lookup(key, false, Integer.MAX_VALUE) typically fails 'assert completions.isComplete' (bug LFV-1003; [repro](../../repro/LFV-1003/))

## Files

- [analyzingsuggester-num-times-analyzed-paths-int-overflow/model/num_wrap_smt.py](analyzingsuggester-num-times-analyzed-paths-int-overflow/model/num_wrap_smt.py) (used for LFV-1003)
- [blendedinfix-num-times-numfactor-int-overflow/model/num_wrap_smt.py](blendedinfix-num-times-numfactor-int-overflow/model/num_wrap_smt.py) (used for LFV-998)
- [nrtsuggester-topn-times-prefix-paths-int-overflow/model/num_wrap_smt.py](nrtsuggester-topn-times-prefix-paths-int-overflow/model/num_wrap_smt.py) (used for LFV-999)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
