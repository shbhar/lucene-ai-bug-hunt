# Formal models and scripts: tests-gui-build-option-not-passed-to-test-jvm

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- Lucene's Gradle build declares the option tests.gui ('Enables or disables @RequiresGUI tests', on by default on CI builds) but does not pass it to the test JVM, so the option has no effect on the test JVM and the only @RequiresGUI test, the Luke launch test, is skipped with -Ptests.gui=true, with -Dtests.gui=true and on CI builds: in the 10.6.0 sources, and on branch_11x and main once the method has its missing @Test back (LFV-389); test infrastructure only (finding LFV-1522; [repro](../../repro/LFV-1522/), [finding page](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1522.html))

## Files

- [tests-gui-build-option-not-inherited-as-test-jvm-system-property/model/option_census.py](tests-gui-build-option-not-inherited-as-test-jvm-system-property/model/option_census.py) (used for LFV-1522)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
