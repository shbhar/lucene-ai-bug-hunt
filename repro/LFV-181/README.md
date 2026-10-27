# Expressions: !, &amp;&amp;, || and ?: keep only the low 32 bits of a bitwise/shift result or hex/octal literal, so a non-zero value such as 1 &lt;&lt; 32 or 0x100000000 counts as false

Bug LFV-181 · Area: expressions / JavascriptCompiler code generation (boolean operators on long-typed operands: bitwise and shift results, hex and octal literals) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You compile a Lucene expression that tests a high bit of a long field, such as '(flags &amp; 0x100000000) ? 10: 1' over a NumericDocValuesField with flags = 2^32 + 1, and expect 10, because 'flags &amp; 0x100000000' evaluates to 4294967296 and '(flags &amp; 0x100000000) != 0' is 1. Instead you get 1: the compiler asks for the operand of !, &amp;&amp;, || and ?: as a 32-bit int and narrows a long-typed operand (the result of &amp; | ^ ~ &lt;&lt; &gt;&gt; &gt;&gt;&gt;, or a hex/octal literal) by keeping only its low 32 bits, so every non-zero value whose low 32 bits are zero counts as false. The same number is true when written as the decimal literal 4294967296, as a variable or as '((1 &lt;&lt; 32) + 0)', but false as '0x100000000' or '(1 &lt;&lt; 32)'.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: You get 1: the compiler asks for the operand of !, &amp;&amp;, || and ?: as a 32-bit int and narrows a long-typed operand (the result of &amp; | ^ ~ &lt;&lt; &gt;&gt; &gt;&gt;&gt;, or a hex/octal literal) by keeping only its low 32 bits, so every non-zero value whose low 32 bits are zero counts as false.

## Files

- [src/org/apache/lucene/expressions/TestBooleanOperandLongNarrowing.java](src/org/apache/lucene/expressions/TestBooleanOperandLongNarrowing.java) (JUnit test)
- [src/BooleanOperandLongNarrowingMain.java](src/BooleanOperandLongNarrowingMain.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/expressions/build/libs/lucene-expressions-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.expressions.TestBooleanOperandLongNarrowing

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" BooleanOperandLongNarrowingMain
```

The module(s) expressions also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
