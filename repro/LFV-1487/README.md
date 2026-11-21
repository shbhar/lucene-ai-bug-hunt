# JoinUtil's scoring or min/max global-ordinals join over a one-segment index returns wrong and infinite scores and loses hits when the from-searcher splits the segment into partitions: since #16805 (main only) the single-segment collectors update the shared ordinal, score and count arrays without the per-block lock

Finding LFV-1487 · Area: join / JoinUtil global-ordinals join with scores or min/max: concurrent collection of one segment in partitions (GlobalOrdinalsWithScoreCollectorManager) · Priority: medium · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1487.html](../../bugs/LFV-1487.html)

AI-assisted finding, confirmed by automated checks. Upstream status: Fix in progress, by uchiha-asha (open PR #17028; an independent fix, opened one day after this row was found, when the row was not yet public) on 2026-11-15 (https://github.com/apache/lucene/pull/17028). Open PR #17028 (uchiha-asha, opened 2026-11-15; no milestone; as of 2026-11-16 CI green, 13 of 13 checks, and one approving review, by kkewwei on 2026-11-16; a further commit followed that day) changes GlobalOrdinalsWithScoreCollectorManager and JoinUtil: one collector serves the one-segment and the ordinal-map case, so the partitions of one segment update the shared match bits, scores and counts under the per-block lock; since the commit of 2026-11-16 the score is computed before the lock is taken. On a build of the pull request as of 2026-11-16 our minimal repro passes 20 of 20 iterations; on main 01deb8e63e0b it fails 20 of 20; each with and without assertions. The PR does not cite this list. This finding was recorded on 2026-11-14, a day before the PR was opened, and was not yet public then; 'an independent fix' means only that the PR does not cite this list. This site files nothing upstream.

## What it shows

You join parent documents to their children with JoinUtil.createJoinQuery(joinField, fromQuery, toQuery, searcher, ScoreMode.Total, null) on an index with one segment, and your searcher splits that segment into partitions so that two threads collect it (the documented intra-segment concurrency); one parent has two children that score 1 each, so you expect the parent to score 2. Instead, on main since #16805, it scores 1.0: for a one-segment index JoinUtil drops the ordinal map, and the collectors used in that case update the shared ordinal bit set and the score and count arrays without the per-block lock that the multi-segment collectors take, so one thread's update overwrites the other's. Without any scheduling help, most joins tried over a larger one-segment index also go wrong (38 of 50 and 31 of 50 in the two latest runs, every join in the earlier ones): parents drop out of the match set, and others get a score of Infinity, -Infinity, NaN or 0 (a top-N search by score does not return a hit that scores -Infinity, which is most of what goes missing under ScoreMode.Max), while release 10.6.0 and the main before #16805, which collect the from side on one thread, are exact.

## Expected vs actual

- Expected: The parent scores 2.0, the sum of its two children's scores (1 + 1).
- Actual: The parent scores 1.0 (`expected:<2.0> but was:<1.0>` at line 76): one child's score is lost.

## Files

- [src/org/apache/lucene/search/join/TestGlobalOrdinalsJoinSegmentPartitions.java](src/org/apache/lucene/search/join/TestGlobalOrdinalsJoinSegmentPartitions.java) (JUnit test)

## Run it

- Tested on Lucene main 7b8418faa2ff (a commit of 2026-11-20): still reproduces.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced.
- What the test does: it runs a ScoreMode.Total join over a one-segment index of one parent and two children that score 1 each, with a searcher that allows segment partitions; it asserts a parent score of 2.0 and gets 1.0. It forces the schedule that shows the finding: the score() of its child query waits, for at most 5 seconds, until both children are being scored at once, so two threads have each read the parent's stored score before either adds to it. Lost hits, infinite scores and the min/max filter named in the title are not part of this test.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestGlobalOrdinalsJoinSegmentPartitions
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestGlobalOrdinalsJoinSegmentPartitions.java](TestGlobalOrdinalsJoinSegmentPartitions.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
