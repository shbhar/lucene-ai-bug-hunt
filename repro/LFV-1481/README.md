# JoinUtil's global-ordinals join with ScoreMode.None and a min or max filter scores every hit 0 instead of 1 since #16805 (main only), so a boost on the join has no effect

Finding LFV-1481 · Area: join / JoinUtil global-ordinals join: the score of a ScoreMode.None join that has a min/max filter (GlobalOrdinalsWithScoreQuery, GlobalOrdinalsWithScoreCollectorManager) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1481.html](../../bugs/LFV-1481.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a global-ordinals join with JoinUtil.createJoinQuery and ScoreMode.None and ask for at least two "from" documents (min = 2); one "to" document has two, so it matches, and you expect the hit to score what the same join scores it without the min/max filter: the constant 1 (times any boost), as on 10.6.0 and every earlier release. Instead, on main since #16805, the hit scores 0.0: the query now asks the collector manager for the score, and for ScoreMode.None the manager reads an empty score array and returns its unset value 0, while the old 'return 1f;' is still in the file with no caller. The hits and counts stay right, but a boost on the join has no effect (0 times the boost), explain() reports 0, and a join clause next to other scoring clauses no longer ranks the joined documents higher.

## Expected vs actual

- Expected: Both joins score their one hit the same: 1.0 with and without `min = 2`, as on 10.6.0 and on the 11.0 branch.
- Actual: The join with `min = 2` scores the hit 0.0: `expected:<1.0> but was:<0.0>` at line 56, with assertions on and off.

## Files

- [src/org/apache/lucene/search/join/TestGlobalOrdinalsJoinNoneScoreWithMin.java](src/org/apache/lucene/search/join/TestGlobalOrdinalsJoinNoneScoreWithMin.java) (JUnit test)

## Run it

- Tested on Lucene main 9965d34e3c77 (2026-11-16): still reproduces.
- Tested on Lucene main 781025d6fe32 (2026-11-13): reproduced.
- What the test does: it indexes one "to" document and two "from" documents that share one join value and compares the score of the ScoreMode.None join with min = 2 with the score of the same join without a min/max filter. It fails because the filtered join scores 0.0 where the unfiltered join scores 1.0. The effects on a boost, on explain() and on ranking named above follow from that score and are not part of this test. Only main has the change: on a build of the 11.0 branch (run on branch_11x 9d2b5a15427a, 2026-11-17) and on the 10.6.0 release the test passes, with assertions on and off; on main 9965d34e3c77 it fails as described.
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
     execute --select-class org.apache.lucene.search.join.TestGlobalOrdinalsJoinNoneScoreWithMin
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestGlobalOrdinalsJoinNoneScoreWithMin.java](TestGlobalOrdinalsJoinNoneScoreWithMin.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
