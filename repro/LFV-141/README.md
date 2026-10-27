# Doc-values range queries from *RangeDocValuesField.newSlowIntersectsQuery base equals/hashCode on the caller's min/max arrays but match the bounds copied at construction, so queries built from reused arrays compare equal while matching different ranges

Bug LFV-141 · Area: document / range-field doc-values queries (Int/Long/Float/DoubleRangeSlowRangeQuery) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a doc-values range query with, for example, IntRangeDocValuesField.newSlowIntersectsQuery(field, min, max), then write new values into the same min and max arrays and build a second query, as a loop that ORs several ranges through one scratch array pair does. You expect two queries that match different ranges to be different queries, as they are with the points factory IntRange.newIntersectsQuery. Instead each query matches the bounds it encoded when it was built but compares equal to (and prints) whatever the arrays hold now, so BooleanQuery de-duplicates the 'equal' clauses and a query cache can hand one query's result to the other: an OR of [0,3] and [5,6] built this way finds 0 hits on a document with range [5,10] instead of 1.

## Expected vs actual

- Expected: You expect two queries that match different ranges to be different queries, as they are with the points factory IntRange.newIntersectsQuery.
- Actual: Each query matches the bounds it encoded when it was built but compares equal to (and prints) whatever the arrays hold now, so BooleanQuery de-duplicates the 'equal' clauses and a query cache can hand one query's result to the other: an OR of [0,3] and [5,6] built this way finds 0 hits on a document with range [5,10] instead of 1.

## Files

- [src/org/apache/lucene/search/TestSlowRangeQueryCallerArrays.java](src/org/apache/lucene/search/TestSlowRangeQueryCallerArrays.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestSlowRangeQueryCallerArrays
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
