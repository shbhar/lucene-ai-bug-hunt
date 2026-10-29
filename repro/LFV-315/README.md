# RescoreTopNQuery builds its inner Weight with ScoreMode.COMPLETE_NO_SCORES, so a values source that needs scores (DoubleValuesSource.SCORES, an expression using _score) reads 0 instead of the inner query's score

Bug LFV-315 · Area: search / rescoring · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-315.html](../../bugs/LFV-315.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap a query in RescoreTopNQuery and re-score its matches with a values source that uses the query's own score, for example DoubleValuesSource.SCORES or an expression such as _score * popularity, keeping the best n. You expect the source to see each match's real score, so rescoring body:common OR body:rare by SCORES with n=1 should return the rare-term document 9 with its normal score. Instead RescoreTopNQuery runs the inner query in a mode that produces no scores, so the source reads 0 for every match and the result is doc 0 with score 0.0 (the first match by doc id); for a kNN inner query the scores are right until the query cache caches the inner query, and from then on (the 5th identical search with the usage-tracking policy) they read 0 too.

The test shows that RescoreTopNQuery with DoubleValuesSource.SCORES reads score 0 for every inner hit (inner Weight built with ScoreMode.COMPLETE_NO_SCORES).

## Expected vs actual

Expected: the TermQuery's own best hit and BM25 score (doc 1, 0.1098); actual on main 645d4fe: doc 0 with score 0.0 (with -ea and -da).

## Files

- [src/org/apache/lucene/search/TestRescoreTopNQueryScoresSource.java](src/org/apache/lucene/search/TestRescoreTopNQueryScoresSource.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestRescoreTopNQueryScoresSource
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
