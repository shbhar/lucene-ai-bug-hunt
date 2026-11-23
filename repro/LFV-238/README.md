# With an executor, grouped search breaks ties by slice number instead of doc ID, so tied groups and tied documents differ from sequential search

Finding LFV-238 · Area: grouping / collector managers (FirstPassGroupingCollectorManager, TopGroupsCollectorManager and BlockGroupingCollectorManager reduce through the cross-shard merges SearchGroup.merge, TopGroups.merge and TopGroups.mergeBlockGroups; GroupingSearch) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a grouped search (GroupingSearch, or the grouping collector managers) on an IndexSearcher that has an executor, and several groups or several documents tie on the sort, for example because every hit scores 1.0. You expect the tie to be broken by doc ID, as Sort's javadoc says and as the same search without an executor does, so the top group and the top documents are the same either way. Instead the per-slice results are combined with merges written for real shards, which break ties by shard index, here the slice number: the search returns doc 1 instead of doc 0, or group g1 instead of g0, and the choice changes whenever the slicing changes.

## Expected vs actual

- Expected: You expect the tie to be broken by doc ID, as Sort's javadoc says and as the same search without an executor does, so the top group and the top documents are the same either way.
- Actual: The per-slice results are combined with merges written for real shards, which break ties by shard index, here the slice number: the search returns doc 1 instead of doc 0, or group g1 instead of g0, and the choice changes whenever the slicing changes.

## Files

- [src/org/apache/lucene/search/grouping/TestConcurrentGroupingDocTieOrder.java](src/org/apache/lucene/search/grouping/TestConcurrentGroupingDocTieOrder.java) (JUnit test)
- [src/org/apache/lucene/search/grouping/TestConcurrentGroupingGroupTieOrder.java](src/org/apache/lucene/search/grouping/TestConcurrentGroupingGroupTieOrder.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): still reproduces (the repro adapted to an API change on this commit). The change: #16962 made the no-argument MatchAllDocsQuery constructor private on main (24d9e7d1a0d) and on the 11.0 branch (branch_11x, a6a26f9b251), so replace new MatchAllDocsQuery() with MatchAllDocsQuery.INSTANCE before you compile the test there.
- Tested on Lucene main ea57360912ed (a commit of 2026-10-24): reproduced.
- The constant of the replacement named above exists from Lucene 10.4.0 on. With the jars of a release from 10.5.0 to 10.6.0 the tests compile as shipped (the collector managers they use do not exist before 10.5.0): for such a release, put the five jars of that release (Maven Central, org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework, lucene-grouping, lucene-queries) in place of the five $LUCENE jars. Run on 2026-11-17, with assertions on and off: with the replacement the six tests fail 6 of 6 on a build of main 9965d34e3c77 and of the 11.0 branch (branch_11x 9d2b5a15427a; there V=11.0.0-SNAPSHOT); with the 10.6.0 jars they fail 6 of 6 as shipped; with the 10.5.0 and the 10.5.2 jars 3 of the 6 fail, because GroupingSearch by a field searched sequentially before 10.6.0.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/grouping/build/libs/lucene-grouping-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.grouping.TestConcurrentGroupingDocTieOrder \
       --select-class org.apache.lucene.search.grouping.TestConcurrentGroupingGroupTieOrder
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
