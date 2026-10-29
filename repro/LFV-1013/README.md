# Four Lucene test methods never run because their names do not start with lowercase 'test' (TestXYRectangle.tesInvalidMinMaxX/Y, TestOperations.TestOptionalOptionalAcceptsState0, TestCommonGramsFilter.TestFirstAndLastStopWord); the two TestXYRectangle ones would also fail if run, because they look for '5 &gt; 4' while XYRectangle reports '5.0 &gt; 4.0', so XYRectangle's reversed-bounds check has had no running test since 2020

Bug LFV-1013 · Area: tests / test-method discovery (core geo TestXYRectangle, core automaton TestOperations, analysis-common TestCommonGramsFilter) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run Lucene's test suite and expect every method written as a test to run. Four are named 'tes...' or 'Test...' instead of 'test...', so Lucene's runner, which picks only public no-arg methods whose names start with lowercase 'test' (or that carry @Test), silently skips TestXYRectangle.tesInvalidMinMaxX/Y, TestOperations.TestOptionalOptionalAcceptsState0 and TestCommonGramsFilter.TestFirstAndLastStopWord. Run under 'test...' names, the two TestXYRectangle methods fail, because they look for '5 &gt; 4' while XYRectangle correctly reports 'minX must be lower than maxX, got 5.0 &gt; 4.0'; the library itself behaves correctly.

The test shows that Lucene's test runner never runs four public no-arg test methods whose names miss the lowercase `test` prefix: `TestXYRectangle#tesInvalidMinMaxX/Y`, `TestOperations#TestOptionalOptionalAcceptsState0`, `TestCommonGramsFilter#TestFirstAndLastStopWord` (the checkout's own, unmodified test files are compiled in).

## Expected vs actual

Expected: every public no-arg instance method of the three classes is among the tests that `Request.aClass(c).getRunner()` (RandomizedRunner) reports; actual: `expected:<[]> but was:<[TestCommonGramsFilter#TestFirstAndLastStopWord, TestOperations#TestOptionalOptionalAcceptsState0, TestXYRectangle#tesInvalidMinMaxX, TestXYRectangle#tesInvalidMinMaxY]>`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/TestMisnamedTestMethodsNeverRun.java](src/org/apache/lucene/TestMisnamedTestMethodsNeverRun.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.TestMisnamedTestMethodsNeverRun
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/assert-only-input-checks-sweep-siblings-of-16919/](../../models/assert-only-input-checks-sweep-siblings-of-16919/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
