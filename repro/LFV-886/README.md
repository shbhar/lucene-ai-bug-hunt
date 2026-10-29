# RangeMapFloatFunction (map) never calls createWeight on its target and default value sources, so a target or default that keeps per-query state in the context (ttf, sttf, or a user ValueSource) fails with NullPointerException

Bug LFV-886 · Area: queries / function queries (RangeMapFloatFunction, map: createWeight propagation to sub-ValueSources) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build map(x, 0, 2, ttf(text, test)) as new RangeMapFloatFunction(source, 0, 2, new TotalTermFreqValueSource(...), null) and score it with FunctionQuery, expecting every document whose x lies in [0, 2] to get the target's value (3 in the repro, the score FunctionQuery(ttf) gives on its own). Instead the search throws NullPointerException ('this.val$targets' is null at RangeMapFloatFunction.java:84); sttf as the default fails the same way (:85), and a user ValueSource that stores a per-query statistic in createWeight, as the ValueSource javadoc describes, finds its context entry missing. The cause: RangeMapFloatFunction.createWeight forwards only to source, while getValues also asks target and default for their values on the same context, whereas every other composite ValueSource forwards createWeight to all of its children.

The test shows that `RangeMapFloatFunction` (`map(x, min, max, target, default)`) never calls `createWeight` on its target, so a target that keeps per-query state in the context (here `sttf(text)`) is read without it.

## Expected vs actual

Expected `FunctionQuery(map(1, 0, 2, sttf(text)))` to score the 1-document index 2 (= `sttf(text)`); actual: `NullPointerException` (`this.val$targets` is null), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/queries/function/TestRangeMapTargetCreateWeight.java](src/org/apache/lucene/queries/function/TestRangeMapTargetCreateWeight.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.function.TestRangeMapTargetCreateWeight
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
