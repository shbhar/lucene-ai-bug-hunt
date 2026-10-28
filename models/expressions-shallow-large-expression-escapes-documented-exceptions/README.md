# Formal models and scripts: expressions-shallow-large-expression-escapes-documented-exceptions

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- JavascriptCompiler.compile lets the bytecode generator's method-size failure escape untranslated: a well-formed, shallowly nested expression that is too big for one JVM method throws ASM's MethodTooLargeException (or an ArrayIndexOutOfBoundsException from ASM's frame computation) on 8.5.0 to 10.5.2 and the 10.6.0 release line, and the class-file API's IllegalArgumentException on main, instead of the documented ParseException / IllegalStateException (bug LFV-906; [repro](../../repro/LFV-906/))

## Files

- [javascriptcompiler-bytecode-limit-escapes-documented-exceptions/model/bytecode_size_model.py](javascriptcompiler-bytecode-limit-escapes-documented-exceptions/model/bytecode_size_model.py) (used for LFV-906)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
