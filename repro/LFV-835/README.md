# PayloadScoreQuery scores a payload value of -0.0 as -0.0 instead of 0 (MinPayloadFunction, MaxPayloadFunction or a custom PayloadFunction with a decoder that reads IEEE floats), because its LUCENE-7996 negative-score clamp tests 'score &gt;= 0 == false' and its explain truncation 'value &lt; 0'; since 10.2.0 the top-1 hit is then not the head of the top-2 hits

Bug LFV-835 · Area: queries / payloads (PayloadScoreQuery payload-score clamp, LUCENE-7996; visible through core TopScoreDocCollector tie handling; sibling of LFV-273's function-query clamps) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

Index two documents whose float payloads are '-0.0' and '0.0' (WhitespaceTokenizer + DelimitedPayloadTokenFilter with FloatEncoder) and search PayloadScoreQuery(SpanTermQuery, MaxPayloadFunction or MinPayloadFunction, a decoder that calls PayloadHelper.decodeFloat, includeSpanScore false or true). You expect both hits to score 0 and tie in doc-id order, because PayloadScoreQuery maps every negative payload score to 0 (LUCENE-7996: 'Scorers are no longer allowed to produce negative scores'; the test framework's AssertingScorer checks Float.compare(score, 0f) &gt;= 0), as it does for a payload of -1.0. Instead the -0.0 document scores and explains as -0.0, because the clamp 'score &gt;= 0 == false' and the explain test 'value &lt; 0' are both false for -0.0, and since 10.2.0 search(q, 1) returns that document while search(q, 2) ranks it second.

## Expected vs actual

- Expected: You expect both hits to score 0 and tie in doc-id order, because PayloadScoreQuery maps every negative payload score to 0 (LUCENE-7996: 'Scorers are no longer allowed to produce negative scores'; the test framework's AssertingScorer checks Float.compare(score, 0f) &gt;= 0), as it does for a payload of -1.0.
- Actual: The -0.0 document scores and explains as -0.0, because the clamp 'score &gt;= 0 == false' and the explain test 'value &lt; 0' are both false for -0.0, and since 10.2.0 search(q, 1) returns that document while search(q, 2) ranks it second.

## Files

- [src/org/apache/lucene/queries/payloads/TestPayloadScoreQueryNegativeZero.java](src/org/apache/lucene/queries/payloads/TestPayloadScoreQueryNegativeZero.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.payloads.TestPayloadScoreQueryNegativeZero
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
