# CSVUtil.parse keeps the enclosing quotes of a field that holds both a comma and a double quote, so it does not decode CSVUtil.quoteEscape output

Bug LFV-889 · Area: analysis-common / util CSVUtil (CSV line parsing for the kuromoji user dictionary and the kuromoji/nori dictionary builders) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You encode a value that holds both a comma and a double quote with CSVUtil.quoteEscape, which doubles the quotes and wraps the value in quotes (the exact output Lucene's own TestCSVUtil.testQuoteEscapeQuotesAndComma expects), put it before another field in a CSV line, and expect CSVUtil.parse to give the value back, as it does for values with only a comma or only a quote. Instead parse unescapes the inner quotes but keeps the enclosing ones: the value a "b", c is encoded as "a ""b"", c" and parse returns "a "b", c" with the outer quotes still on it. The cause is the unquote regex ^"([^"]+)"$ (CSVUtil.java:29), which strips enclosing quotes only from a field that has no quote inside.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Parse unescapes the inner quotes but keeps the enclosing ones: the value a "b", c is encoded as "a ""b"", c" and parse returns "a "b", c" with the outer quotes still on it.

## Files

- [src/org/apache/lucene/analysis/util/TestCSVUtilCommaQuoteRoundTrip.java](src/org/apache/lucene/analysis/util/TestCSVUtilCommaQuoteRoundTrip.java) (JUnit test)
- [src/org/apache/lucene/analysis/util/TestCSVUtilExhaustiveRoundTrip.java](src/org/apache/lucene/analysis/util/TestCSVUtilExhaustiveRoundTrip.java) (JUnit test)
- [src/org/apache/lucene/analysis/util/TestCSVUtilControls.java](src/org/apache/lucene/analysis/util/TestCSVUtilControls.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.util.TestCSVUtilCommaQuoteRoundTrip \
       --select-class org.apache.lucene.analysis.util.TestCSVUtilExhaustiveRoundTrip \
       --select-class org.apache.lucene.analysis.util.TestCSVUtilControls
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
