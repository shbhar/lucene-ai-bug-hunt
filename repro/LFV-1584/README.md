# A custom GroupReducer whose per-group collector prunes by score (run: a stock top-1 TopScoreDocCollector with a total-hits threshold of 1) can leave another group's collector without its documents, with no error: SecondPassGroupingCollector declares ScoreMode.COMPLETE and GroupReducer#setScorer hands the search's one Scorable to every group's leaf collector as it is (run with a TermQuery: a first test over four documents on each release from 10.3.0 to 10.6.0, branch_11x and main; the shipped test over three documents of equal score on 10.6.0, branch_11x and main; known upstream by its mechanism, #15239)

Finding LFV-1584 · Area: grouping / second pass: GroupReducer and SecondPassGroupingCollector (the per-group collectors share the search's Scorable) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You write a GroupReducer that gives every group a stock top-1 TopScoreDocCollector with a total-hits threshold of 1 (so a group's collector may skip hits that cannot enter its own list) and run a TermQuery through SecondPassGroupingCollector over one segment of four documents in which group a's two documents come first and score higher than group b's two. You expect group b's collector to return document 3, the group's best document, as GroupReducer's javadoc says ('LeafCollector#collect(int) is called for each document that is in a group') and as the same search gives on 10.2.2. Instead, with these four documents, on each release from 10.3.0 to 10.6.0 and on branch_11x and main (both unreleased), group b's collector returns no hit, with no error (where the calls were traced, on 10.6.0 and branch_11x, it is handed no document at all): every group's collector is handed the search's one Scorable, group a's collector sets a minimum competitive score on it at its second document, and the bulk scorer of the TermQuery then drops each later document that scores below that value, although SecondPassGroupingCollector declared ScoreMode.COMPLETE. The shipped test has three documents of equal score in one segment (documents 0 and 1 in group a, document 2 in group b) and the same reducer and query: group b's collector returns no hit where the group has one matching document, on 10.6.0, branch_11x and main, with and without assertions, and the test passes on 10.2.2 (run with and without assertions); it was not run on 10.3.0 to 10.5.2.

## Expected vs actual

- Expected: You expect group b's collector to return document 3, the group's best document, as GroupReducer's javadoc says ('LeafCollector#collect(int) is called for each document that is in a group') and as the same search gives on 10.2.2.
- Actual: With these four documents, on each release from 10.3.0 to 10.6.0 and on branch_11x and main (both unreleased), group b's collector returns no hit, with no error (where the calls were traced, on 10.6.0 and branch_11x, it is handed no document at all): every group's collector is handed the search's one Scorable, group a's collector sets a minimum competitive score on it at its second document, and the bulk scorer of the TermQuery then drops each later document that scores below that value, although SecondPassGroupingCollector declared ScoreMode.COMPLETE.

## Files

- [src/TestGroupReducerSharedScorable.java](src/TestGroupReducerSharedScorable.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): reproduced, with assertions on and off.
- Tested on Lucene branch_11x 262e019500ec (a commit of 2026-11-22): reproduced, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- Not new in 11.0: the test fails the same way with the 10.6.0 release jars, on a build of branch_11x (262e019500ec) and on Lucene main, with and without assertions. Known upstream by its mechanism: the Lucene issue #15239 (another person's; open when read on 2026-11-23) describes it for an application's own collector and does not name the grouping module. The limits of the finding: no path in Lucene's main sources builds such a reducer (the two reducers there, in TopGroupsCollector and DistinctValuesCollector, use exhaustive per-group collectors, as read at main b5a4546a5964); the threshold Integer.MAX_VALUE and a guard inside the per-group collector were right in the runs; and SecondPassGroupingCollector is marked @lucene.experimental. Which test this block runs: the small test of this folder, TestGroupReducerSharedScorable, a plain JUnit 4 class with one method. It indexes three documents with the same one-term field, so with equal scores, into one segment, documents 0 and 1 in group a and document 2 in group b, and runs a TermQuery through SecondPassGroupingCollector with a GroupReducer of its own whose newCollector() returns a stock TopScoreDocCollector for 1 hit with a total-hits threshold of 1 (new TopScoreDocCollectorManager(1, null, 1).newCollector()); it expects group b's collector to return one hit, the group's one matching document. Group b's collector returns no hit: java.lang.AssertionError: expected:&lt;1&gt; but was:&lt;0&gt;; Lucene throws no exception. Control: with the 10.2.2 release jars the same block reports 1 test successful, with and without assertions. The lines under 'Expected vs actual' above and the first part of 'What it shows' describe a first test over four documents (group a's two documents score higher than group b's two) that is not shipped here: it fails on each release from 10.3.0 to 10.6.0, on branch_11x and on main, with and without assertions, and passes on 10.2.2; the shipped test was run on 10.6.0, branch_11x 262e019500ec, main b5a4546a5964 and 10.2.2 and not on 10.3.0 to 10.5.2. The test draws nothing from the seed. On a branch_11x checkout set V=11.0.0-SNAPSHOT; for a release, set V to its version and use the five jars of that release from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework, lucene-grouping, lucene-queries) in place of the five $LUCENE jars. For the Gradle alternative at the end of this page the module is lucene/grouping: with the test copied to lucene/grouping/src/test/ it reports 1 test completed, 1 failed, with the same message, on branch_11x 262e019500ec and on main b5a4546a5964, with -Ptests.asserts=true and with -Ptests.asserts=false.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.AssertionError: expected:&lt;1&gt; but was:&lt;0&gt; (group b's collector returns no hit where the group has one matching document); Lucene throws no exception.
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
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestGroupReducerSharedScorable
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
