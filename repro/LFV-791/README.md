# A relevance-first TopFieldCollector (Sort.RELEVANCE or Sort(FIELD_SCORE, tiebreak)) never sets the minimum competitive score when totalHitsThreshold is crossed, so skipping starts only if a later hit is competitive, and the search can score every match (performance regression since 8.7.0; results correct)

Bug LFV-791 · Area: search / sorting (TopFieldCollector, relevance-first sort: minimum competitive score at the totalHitsThreshold crossing) · Priority: low · Impact: minor · Novelty: new

Bug page: [../../bugs/LFV-791.html](../../bugs/LFV-791.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run IndexSearcher#search(query, 10, Sort.RELEVANCE), or a searchAfter page sorted by Sort(SortField.FIELD_SCORE, tiebreak), on a query with many matches, and expect it to start skipping non-competitive documents once 1,000 hits are counted, as search(query, 10) does and as LUCENE-8507 made relevance-first TopFieldCollector do from 8.0.0 to 8.6.3. Instead TopFieldCollector.countHit flips totalHitsRelation to GREATER_THAN_OR_EQUAL_TO on the hit that crosses the threshold, before thresholdCheck's EQUAL_TO-guarded updateMinCompetitiveScore call runs, so that call can never take effect and the minimum competitive score is set only when a later hit is competitive; with the best documents first, the relevance-sorted search scores all 10,011 matches where search(query, 10) stops at 1,024, with the same top 10. This is a regression from LUCENE-9449 (8.7.0); results are always correct.

The test shows that a relevance-first TopFieldCollector (`Sort.RELEVANCE`, numHits 1, totalHitsThreshold 1) never calls `Scorable#setMinCompetitiveScore` when the threshold is exceeded by a non-competitive hit, so skipping cannot start.

## Expected vs actual

Expected: after hits scored 2 and 1, a min competitive score &gt;= 2 (the queue bottom, as TopScoreDocCollector does); actual (-ea and -da): `expected min competitive score >= 2, got 0.0`.

## Files

- [src/org/apache/lucene/search/TestRelevanceSortMinCompetitiveScore.java](src/org/apache/lucene/search/TestRelevanceSortMinCompetitiveScore.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestRelevanceSortMinCompetitiveScore
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
