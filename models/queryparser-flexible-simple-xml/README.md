# Formal models and scripts: queryparser-flexible-simple-xml

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Flexible query parser: QueryNode#toQueryString prints field names without escaping, so nodes for escaped field names such as 'my field' or 'a:b' print strings that parse to a different query or do not parse (bug LFV-356; [repro](../../repro/LFV-356/))
- Flexible query parser: EscapeQuerySyntaxImpl does not escape '@' after the first character of a term or a term that is exactly &amp;&amp; or ||, so QueryNode#toQueryString output for such terms does not parse (bug LFV-398; [repro](../../repro/LFV-398/))

## Files

- [flexible-escaper-leaves-at-sign-and-andor-words-unescaped/model/classify_syntax.py](flexible-escaper-leaves-at-sign-and-andor-words-unescaped/model/classify_syntax.py) (used for LFV-398)
- [flexible-toquerystring-field-names-not-escaped/model/classify_syntax.py](flexible-toquerystring-field-names-not-escaped/model/classify_syntax.py) (used for LFV-356)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
