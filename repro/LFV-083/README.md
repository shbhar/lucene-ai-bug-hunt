# FunctionRangeQuery over Int/Long/EnumFieldSource turns an exclusive bound at the type limit into an inclusive one

Bug LFV-083 · Area: queries / function · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, reproduced on Lucene main but not yet independently re-verified, and not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a FunctionRangeQuery over an IntFieldSource, LongFieldSource or EnumFieldSource with an exclusive bound exactly at the type limit, such as n &gt; Integer.MAX_VALUE. You expect no document to match. Instead getRangeScorer adjusts an exclusive bound only when it is not already at the limit (if (!includeLower &amp;&amp; lower &lt; Integer.MAX_VALUE) lower++), so at the limit the bound stays inclusive and the document whose value is exactly MAX (or MIN) matches: 1 hit instead of 0.

The test shows that FunctionRangeQuery over IntFieldSource with an exclusive lower bound at Integer.MAX_VALUE (n &gt; MAX) still matches the document whose value is MAX.

## Expected vs actual

Expected 0 hits; actual 1 (`expected:<0> but was:<1>`).

## Files

- [src/org/apache/lucene/queries/function/TestFunctionRangeQueryExclusiveLimit.java](src/org/apache/lucene/queries/function/TestFunctionRangeQueryExclusiveLimit.java) (JUnit test)

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
     execute --select-class org.apache.lucene.queries.function.TestFunctionRangeQueryExclusiveLimit
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
