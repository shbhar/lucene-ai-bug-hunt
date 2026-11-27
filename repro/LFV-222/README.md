# A MUST_NOT clause next to a scoring ToParentBlockJoinQuery can be checked against child documents instead of parents (when the join is the query's only positive clause), so excluded parents can be returned, and the search can throw when a parent/child block straddles a 4096-doc window

Finding LFV-222 · Area: join / block join (ToParentBlockJoinQuery.BlockJoinBulkScorer) x search / BooleanQuery MUST_NOT bulk scoring (ReqExclBulkScorer, #16669) · Priority: high · Impact: silent wrong results · Novelty: known upstream

Finding page: [https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-222.html](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-222.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index parent/child blocks and search BooleanQuery(MUST ToParentBlockJoinQuery(skill:java, parentsFilter, ScoreMode.Avg), MUST_NOT status:hidden), expecting only the parents that are not hidden, which is what ScoreMode.None returns, and count() of the same query says 1. Instead, on main the hidden parent p2 comes back for Avg, Max, Min and Total ([p1, p2] instead of [p1]); a MUST_NOT clause that matches only child documents (MUST_NOT skill:java) removes both parents ([] instead of [p1, p2]); and in a 6,000-doc segment where a block straddles doc 4096 and its child before the boundary matches, the search throws AssertionError in ReqExclBulkScorer$WindowBits.get with -ea and ArrayIndexOutOfBoundsException (index -1) with -da. The cause is that since #16669 ReqExclBulkScorer folds the MUST_NOT matches of each 4096-doc window into a bit set that it passes to the required clause as acceptDocs, and the join's BlockJoinBulkScorer forwards that acceptDocs unchanged to its child scorer (ToParentBlockJoinQuery.java:541), so the exclusions are tested on child doc IDs, some of them below the window, and never on the parents it collects.

The test shows that a `MUST_NOT` clause next to a scoring `ToParentBlockJoinQuery` can be checked against child documents instead of parents, so an excluded parent is returned (TestBlockJoinMustNotExcludesParent#testMustNotExcludesParent).

## Expected vs actual

Expected `search(q, 10)` to return only `p1` (`count(q)` is 1); actual `[p1, p2]` (`expected:<[p1]> but was:<[p1, p2]>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/join/TestBlockJoinMustNotExcludesParent.java](src/org/apache/lucene/search/join/TestBlockJoinMustNotExcludesParent.java) (JUnit test)

## Run it

- Tested on Lucene main 82cf7465bb8f (a commit of 2026-11-26): still reproduces.
- Tested on Lucene main 645d4fe14a20 (a commit of 2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
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

# run 1: assertions on, seed 5EED0222
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=5EED0222 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestBlockJoinMustNotExcludesParent
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
