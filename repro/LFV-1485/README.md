# JoinUtil's term join returns ScoreMode.Avg and ScoreMode.Total scores that are off in the last ulp and, with a concurrent from-searcher, change with how the index is sliced: since #16805 (main only; fixed there by #17030 on 2026-11-17) GenericTermsCollector.Manager.reduce rounds each collector's double sum to float before merging and before the Avg division, undoing the double accumulation that #16378 added in 10.6.0

Finding LFV-1485 · Area: join / JoinUtil term join: merging the collectors' score sums for ScoreMode.Total and ScoreMode.Avg (GenericTermsCollector.Manager.reduce) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks. Upstream status: Fixed, by kkewwei (PR #17030, approved and merged by Michael Sokolov; an independent fix, opened two days after this row was found, when the row was not yet public) on 2026-11-17 (https://github.com/apache/lucene/pull/17030). kkewwei fixed it in PR #17030 (opened 2026-11-16): the reduce step of GenericTermsCollector keeps the merged term-join scores in double precision and narrows them to float once. Michael Sokolov approved the PR and merged it into main on 2026-11-17 as 1cfa86310f7 (milestone 12.0.0). The merged commit is the change we ran on 2026-11-16, unchanged. The PR names a CI failure of TestJoinUtil.testMultiValueRandomJoin and does not cite this list. Our minimal repro and the row's slicing test fail on 596ef11ddf96, the commit before the fix, and pass on main 1cfa86310f76, with and without assertions; with the class from before the fix put back on 1cfa86310f76 the repro fails again. Upstream's TestJoinUtil.testMultiValueRandomJoin with the seeds of 23 public build mails (2026-11-10 to 2026-11-16) and of the CI job the PR names fails on 596ef11ddf96 and passes on 1cfa86310f76, 24 of 24. The defect was on main only and in no release; branch_11x (89e68e0f3a50) does not have the change that introduced it. This finding is listed as known upstream because of the build mails. It was recorded on 2026-11-14, two days before the PR was opened, and was not yet public then; 'an independent fix' means only that the PR does not cite this list. This site files nothing upstream.

Fixed upstream: [#17030](https://github.com/apache/lucene/pull/17030) (commit [1cfa86310f76](https://github.com/apache/lucene/commit/1cfa86310f76547a561238b1a05f6250cd1cedb7)), merged 2026-11-17. A defect on main only (from 2026-11-09), in no release: found on 2026-11-14 while main had it, and fixed on main on 2026-11-17, about four hours before the finding was first published on this list.

## What it shows

You index one parent and three children and join them with JoinUtil.createJoinQuery(..., ScoreMode.Avg), where each child scores exactly 1.7 (a BoostQuery over a constant-score query), so you expect the parent to score 1.7, the average of its child scores, as on 10.6.0. Instead, on main since #16805, it scores 1.7000002: the new GenericTermsCollector.Manager.reduce rounds the double sum of the child scores to float and then divides by the count in float, where 10.6.0 divided in double and rounded once. With a from-searcher that collects in several slices the per-slice sums are also rounded to float before they are added, so Total and Avg scores then depend on how the index is sliced. The hits are always right, and releases up to 10.5.2 returned the same 1.7000002 for this test from an older float accumulation that #16378 fixed in 10.6.0. Fixed on main by #17030 (1cfa86310f76, merged 2026-11-17), before any release had the #16805 change.

## Expected vs actual

- Expected: You expect the parent to score 1.7, the average of its child scores, as on 10.6.0.
- Actual: On main since #16805, it scores 1.7000002: the new GenericTermsCollector.Manager.reduce rounds the double sum of the child scores to float and then divides by the count in float, where 10.6.0 divided in double and rounded once.

## Files

- [src/org/apache/lucene/search/join/TestTermJoinAvgScore.java](src/org/apache/lucene/search/join/TestTermJoinAvgScore.java) (JUnit test)

## Run it

- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced.
- What the test does: it indexes one parent and three children that each score 1.7 and joins them with ScoreMode.Avg through a plain IndexSearcher without an executor; it asserts a parent score of 1.7 and gets 1.7000002. The dependence of Total and Avg scores on the slices of a concurrent from-searcher is not part of this test. Fixed on main since 1cfa86310f76 (#17030, merged 2026-11-17): the test passes on that commit, with and without assertions. It fails on 781025d6fe32, the tested commit above, and on 596ef11ddf96, the last commit before the fix; to see it fail, build 781025d6fe32 or 596ef11ddf96.
- This finding was re-run on Lucene main 82cf7465bb8f on 2026-11-26: it no longer reproduces there.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestTermJoinAvgScore
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
