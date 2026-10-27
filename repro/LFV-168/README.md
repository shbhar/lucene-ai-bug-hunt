# Global-ordinals join explain ignores toQuery, so documents that are not hits (from-side documents, or to-side documents a narrower toQuery excludes) are explained as matches

Bug LFV-168 · Area: join / JoinUtil global-ordinals join explain (GlobalOrdinalsQuery, GlobalOrdinalsWithScoreQuery) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a global-ordinals join with JoinUtil.createJoinQuery(joinField, fromQuery, toQuery, searcher, scoreMode, ordinalMap), where 'from' and 'to' documents share one join field and fromQuery/toQuery tell them apart as the javadoc requires, and ask IndexSearcher.explain about a document the join does not return, for example a 'from' document whose join value was collected. You expect a non-match explanation, as upstream TestJoinUtil#testOrdinalsJoinExplainNoMatches asserts for other non-hits of this query and as CheckHits.checkNoMatchExplanations requires. Instead explain checks only that the document's join value was collected and never consults toQuery, which the scorers do iterate, so it answers 'A match, join value X' in every ScoreMode; search results are unaffected, but BooleanQuery explanations built on it go wrong and BooleanWeight.explain can throw NullPointerException.

## Expected vs actual

- Expected: You expect a non-match explanation, as upstream TestJoinUtil#testOrdinalsJoinExplainNoMatches asserts for other non-hits of this query and as CheckHits.checkNoMatchExplanations requires.
- Actual: explain checks only that the document's join value was collected and never consults toQuery, which the scorers do iterate, so it answers 'A match, join value X' in every ScoreMode; search results are unaffected, but BooleanQuery explanations built on it go wrong and BooleanWeight.explain can throw NullPointerException.

## Files

- [src/org/apache/lucene/search/join/TestGlobalOrdinalsJoinExplain.java](src/org/apache/lucene/search/join/TestGlobalOrdinalsJoinExplain.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/join/build/libs/lucene-join-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestGlobalOrdinalsJoinExplain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
