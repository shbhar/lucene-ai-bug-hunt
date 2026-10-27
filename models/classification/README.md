# Formal models and scripts: classification

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- SimpleNaiveBayesDocumentClassifier applies a documented 'field^boost' (e.g. 'title^10') only on its first call: it writes the stripped field name back into the caller's textFieldNames array, so every later classification silently returns the unboosted result (released since 6.0.0) (bug LFV-382; [repro](../../repro/LFV-382/), [bug page](../../bugs/LFV-382.html))
- SimpleNaiveBayesDocumentClassifier returns NaN for every class and null from assignClass when the document has a field value with no tokens (empty, stop words only or punctuation only), because each value's log-likelihood is divided by its token count (released since 6.0.0) (bug LFV-380; [repro](../../repro/LFV-380/), [bug page](../../bugs/LFV-380.html))
- BooleanPerceptronClassifier adds its term weights to its FST in Java String (UTF-16) order instead of the UTF-8 byte order the FST requires, so a text field with a term starting with a supplementary character (such as an emoji) and a term starting with a character in U+E000..U+FFFF (such as a fullwidth letter) makes training throw AssertionError with -ea and, with -da, silently loses term weights (in some term layouts all of them) or throws ArrayIndexOutOfBoundsException (released since 5.0.0) (bug LFV-395; [repro](../../repro/LFV-395/), [bug page](../../bugs/LFV-395.html))
- BM25NBClassifier scores a class that has no live, training-query-matching document as if it had probability 1 (log prior 0 and log-likelihood 0), so with few classes it outranks the trained classes and is assigned although it has no training data (released since 7.0.0) (bug LFV-388; [repro](../../repro/LFV-388/), [bug page](../../bugs/LFV-388.html))

## Files

- [bm25nb-untrained-class-neutral-score/model/raw_score_sites.py](bm25nb-untrained-class-neutral-score/model/raw_score_sites.py) (used for LFV-388)
- [perceptron-fst-utf16-input-order/model/utf_order.py](perceptron-fst-utf16-input-order/model/utf_order.py) (used for LFV-395)
- [snbdoc-boost-stripped-after-first-call/model/raw_score_sites.py](snbdoc-boost-stripped-after-first-call/model/raw_score_sites.py) (used for LFV-382)
- [snbdoc-zero-token-field-nan/model/nb_norm_lemmas.py](snbdoc-zero-token-field-nan/model/nb_norm_lemmas.py) (used for LFV-380)
- [snbdoc-zero-token-field-nan/model/raw_score_sites.py](snbdoc-zero-token-field-nan/model/raw_score_sites.py) (used for LFV-380)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
