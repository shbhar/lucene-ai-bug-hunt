# A scoring JoinUtil term join (ScoreMode Max, Min, Avg or Total, run where scores are needed) throws IllegalArgumentException 'cost must be &gt;= 0' on a segment whose Terms#size() for the join's to-field returns -1, the value its javadoc documents for a term count that is not stored: TermsIncludingScoreQuery multiplies maxDoc by it. Run with an application's reader view that reports -1; on 10.6.0, branch_11x and main the stock postings formats and six stock reader views that were run report a real count (the join throws on 6.1.0 to 10.6.0, branch_11x and main)

Finding LFV-1539 · Area: join / JoinUtil scoring term join (TermsIncludingScoreQuery: the cost its scorer supplier computes from Terms#size(); BitSetIterator's constructor check) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1539.html](../../bugs/LFV-1539.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You put an index reader behind a view whose Terms#size() returns -1, the value the javadoc documents for a term count that is not stored, and run a JoinUtil term join that maps scores (ScoreMode.Max) on it. You expect the hit and the score that the plain reader gives; the same view answers the same join without scores (ScoreMode.None) correctly. Instead IndexSearcher.search throws IllegalArgumentException 'cost must be &gt;= 0, got -2' (the test's segment has two documents): to estimate its cost the join's query multiplies the segment's maxDoc by the to-field's term count, gets a negative number and hands it to an iterator whose constructor rejects it. Read without such a view, an index written with the stock postings formats that were run on 10.6.0, branch_11x and main did not show it: they report a real count.

## Expected vs actual

- Expected: `searcher.search(join, 10)` returns 1 hit, the "to" document, as the plain reader does and as the same view does for the join without scores.
- Actual: `java.lang.IllegalArgumentException: cost must be >= 0, got -2`, thrown by `BitSetIterator`'s constructor while the join's scorer is built.

## Files

- [src/org/apache/lucene/search/join/TestScoringJoinUnknownTermCount.java](src/org/apache/lucene/search/join/TestScoringJoinUnknownTermCount.java) (JUnit test)

## Run it

- Tested on Lucene main 11c8b6ea99ae (a commit of 2026-11-23): still reproduces.
- Tested on Lucene branch_11x 89e68e0f3a50 (a commit of 2026-11-17): reproduced.
- A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there. Against the 10.6.0 release jars the test as shipped does not compile, because LeafReader#terms declares IOException there: on line 38 write 'public Terms terms(String field) throws java.io.IOException'. With that change it fails 1 of 1 on those jars, with and without assertions, with the same message.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.IllegalArgumentException: cost must be &gt;= 0, got -2 (thrown by the constructor of BitSetIterator inside the search call of the test's line 64, so the test's own comparison is never made).
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestScoringJoinUnknownTermCount
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestScoringJoinUnknownTermCount.java](TestScoringJoinUnknownTermCount.java)

Formal models and scripts this finding's part of the hunt used: [../../models/scoring-term-join-unknown-term-count/](../../models/scoring-term-join-unknown-term-count/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
