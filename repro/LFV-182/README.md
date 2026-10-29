# JavascriptCompiler.compile(source, functions, Integer.MAX_VALUE) rejects every expression, even '1', because maxNestingDepth + 1 overflows to Integer.MIN_VALUE

Bug LFV-182 · Area: expressions / JavascriptCompiler nesting-depth guard (compile(String, Map, int maxNestingDepth), new in #16423, unreleased) · Priority: low · Impact: wrong error · Novelty: new

Bug page: [../../bugs/LFV-182.html](../../bugs/LFV-182.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You compile an expression with the new overload JavascriptCompiler.compile(source, functions, maxNestingDepth) and pass Integer.MAX_VALUE to ask for no practical nesting limit, and you expect it to accept at least everything that Integer.MAX_VALUE - 1 accepts, because the parameter is documented only as 'the maximum depth of nesting', with no bound. Instead every expression, even '1', fails with ParseException 'Nesting level too deep (&gt;-2147483648)': the parse-time depth listener is created with maxNestingDepth + 1 to account for the root node, that int addition wraps to Integer.MIN_VALUE, and the depth check fails on the first parser rule. Integer.MAX_VALUE - 1 and the default limit of 1024 work, and the overload is in no release yet, so it can be fixed before 10.6.0 ships.

The test shows that `JavascriptCompiler.compile("1", DEFAULT_FUNCTIONS, Integer.MAX_VALUE)` rejects even the trivial expression `1`, because the parse-time nesting listener is created with `maxNestingDepth + 1`, which wraps to `Integer.MIN_VALUE`.

## Expected vs actual

Expected: `1` compiles. Actual: `ParseException: Invalid expression '1': Nesting level too deep (>-2147483648)`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/expressions/js/TestMaxNestingDepthMaxValue.java](src/org/apache/lucene/expressions/js/TestMaxNestingDepthMaxValue.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 4 test(s) run, 1 failed.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
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
     execute --select-class org.apache.lucene.expressions.js.TestMaxNestingDepthMaxValue
```

The module(s) expressions also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
