# ValueSource#asDoubleValuesSource and #asLongValuesSource never call createWeight, so a ValueSource that keeps per-query state in its context (ttf, sttf, or a user ValueSource) fails with NullPointerException through FunctionScoreQuery, its explain, or a LongValuesSource sort

Finding LFV-893 · Area: queries / function queries (ValueSource to DoubleValuesSource/LongValuesSource bridge: asDoubleValuesSource, asLongValuesSource) · Priority: low · Impact: crash · Novelty: new

Finding page: [../../bugs/LFV-893.html](../../bugs/LFV-893.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You expose a ValueSource as a DoubleValuesSource with asDoubleValuesSource() and score with it through FunctionScoreQuery (for example FunctionScoreQuery.boostByValue, the documented replacement for the removed BoostedQuery), or sort by asLongValuesSource().getSortField(), and expect the per-document values the same ValueSource gives through FunctionQuery or its own getSortField (2 for ttf(text, test) in the repro). Instead the bridge builds a fresh context in getValues and explain and calls in.getValues without ever calling in.createWeight, so ttf and sttf, which compute their statistic in createWeight and return context.get(this) from getValues, throw NullPointerException ('this.val$fv' is null at ValueSource.java:210 or :144), and a user ValueSource that stores per-query state in createWeight, as the ValueSource javadoc describes, finds its context entry missing. FunctionQuery, FunctionRangeQuery and ValueSource#getSortField all call createWeight first, and the bridge's rewrite(searcher) already holds the searcher such a call needs.

The test shows that `ValueSource#asDoubleValuesSource` never calls `createWeight`, so `FunctionScoreQuery.boostByValue(new MatchAllDocsQuery(), ttf.asDoubleValuesSource())` cannot score with `ttf(text, test)`.

## Expected vs actual

Expected score 2 (the ttf of the one document "test test", as `FunctionQuery` gives); actual `NullPointerException` ("this.val$fv" is null at ValueSource.java:210), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/queries/function/TestValueSourceBridgeTotalTermFreq.java](src/org/apache/lucene/queries/function/TestValueSourceBridgeTotalTermFreq.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (a commit of 2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
- The test calls new MatchAllDocsQuery(). On Lucene main (since 24d9e7d1a0d) and on the 11.0 branch (branch_11x, since a6a26f9b251) that constructor is private (#16962, merged 2026-11-03), so on a build of either replace new MatchAllDocsQuery() with MatchAllDocsQuery.INSTANCE before you compile the test; the constant exists from Lucene 10.4.0 on, and with the jars of a release from 9.1.0 to 10.6.0 the test compiles as shipped (for such a release, put the four jars of that release, Maven Central, org.apache.lucene, in place of the four $LUCENE jars). Run on 2026-11-17, with assertions on and off: with the replacement the test fails 1 of 1 on a build of main 9965d34e3c77 (V=12.0.0-SNAPSHOT) and of the 11.0 branch (branch_11x 9d2b5a15427a, V=11.0.0-SNAPSHOT), and as shipped it fails 1 of 1 with the 10.6.0 jars, each time with the NullPointerException at ValueSource.java:210.
- This finding was re-run on Lucene main 1ae70c6e45b8 on 2026-11-19: still reproduces (the repro adapted to an API change on this commit); in the commands below, V=12.0.0-SNAPSHOT on a build of main 1ae70c6e45b8.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
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
     execute --select-class org.apache.lucene.queries.function.TestValueSourceBridgeTotalTermFreq
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
