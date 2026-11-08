# JoinUtil's numeric join reads a DoubleField or FloatField from field as raw IEEE bits, so a join on a negative value silently misses its matches

Finding LFV-1111 · Area: join / JoinUtil numeric (point-based) query-time join: multi-valued Float/Double decode of core DoubleField/FloatField doc values · Priority: medium · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1111.html](../../bugs/LFV-1111.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index one from document with DoubleField("from", -5.0) and one to document with DoubleField("to", -5.0), and count JoinUtil.createJoinQuery("from", true, "to", Double.class, new MatchAllDocsQuery(), fromSearcher, ScoreMode.None) on the to index; you expect 1, because the javadoc lists floats and doubles and allows SORTED_NUMERIC from fields, which is what DoubleField writes. You get 0 with no error: DoubleField stores NumericUtils.doubleToSortableLong(-5.0), but JoinUtil's multi-valued Float/Double path decodes the stored long as raw IEEE bits (Double.longBitsToDouble, Float.intBitsToFloat), so the join looks up -0.8749999999999999 instead of -5.0. Positive values are unaffected because their sortable and raw bits are the same, and FloatField fails the same way.

## Expected vs actual

- Expected: 1 hit: the document's from value -5.0 matches its own point -5.0.
- Actual: 0 hits, no error: `AssertionError: expected:<1> but was:<0>`, with assertions on and off.

## Files

- [src/org/apache/lucene/search/join/TestJoinNegativeDoubleField.java](src/org/apache/lucene/search/join/TestJoinNegativeDoubleField.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces (the repro adapted to an API change on this commit).
- Tested on Lucene main e1faeba08bde (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/join/build/libs/lucene-join-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestJoinNegativeDoubleField
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestJoinNegativeDoubleField.java](TestJoinNegativeDoubleField.java)

Formal models and scripts this finding's part of the hunt used: [../../models/sortednumeric-double-float-sortable-vs-raw-decoding-facet-and-join/](../../models/sortednumeric-double-float-sortable-vs-raw-decoding-facet-and-join/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
