# Sandbox SortedNumericDocValuesMultiRangeQuery classifies each clause by the sign of 'lower - upper', which overflows, so a legal half-open clause such as [Long.MIN_VALUE, 10] or [-5, Long.MAX_VALUE] given together with another clause is silently dropped as if it were reversed (values 5 and 150 with the clauses [Long.MIN_VALUE, 10] and [100, 200] count 1, not 2); since 10.3.0

Finding LFV-1463 · Area: sandbox / doc-values multi-range query construction (DocValuesMultiRangeQuery.SortedNumericStabbingBuilder -> SortedNumericDocValuesMultiRangeQuery clause classification before the overlap merge) · Priority: medium · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1463.html](../../bugs/LFV-1463.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index two documents whose field f (SortedNumericDocValuesField) holds 5 and 150 and count the hits of new DocValuesMultiRangeQuery.SortedNumericStabbingBuilder("f").add(Long.MIN_VALUE, 10).add(100, 200).build(), that is 'f &lt;= 10 OR 100 &lt;= f &lt;= 200' written with the open-end convention that newSlowRangeQuery documents; you expect 2, since the query is documented as the union of its ranges. Instead the count is 1 and the query prints f:[100..200]: building the query sorts out reversed clauses by the sign of 'lower - upper', and Long.MIN_VALUE - 10 overflows to a large positive number, so the legal clause is thrown away as if it were reversed. Every clause wider than 2^63 is lost the same way, while one clause alone takes a different code path and is right.

## Expected vs actual

- Expected: Count 2: f = 5 matches [Long.MIN_VALUE, 10] and f = 150 matches [100, 200].
- Actual: `AssertionError: expected:<2> but was:<1>`: the query kept only [100..200].

## Files

- [src/org/apache/lucene/sandbox/search/TestNumericMultiRangeHalfOpenClause.java](src/org/apache/lucene/sandbox/search/TestNumericMultiRangeHalfOpenClause.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.sandbox.search.TestNumericMultiRangeHalfOpenClause
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestNumericMultiRangeHalfOpenClause.java](TestNumericMultiRangeHalfOpenClause.java)

Formal models and scripts this finding's part of the hunt used: [../../models/sandbox-sortednumeric-multirange-query-long-subtraction-overflow-drops-half-open-clauses-as-reversed-silent-missing-hits-all-dropped-nosuchelement-since-10-3-0-verify/](../../models/sandbox-sortednumeric-multirange-query-long-subtraction-overflow-drops-half-open-clauses-as-reversed-silent-missing-hits-all-dropped-nosuchelement-since-10-3-0-verify/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
