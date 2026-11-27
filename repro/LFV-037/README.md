# MMapDirectory loses shared-arena grouping when a closing arena removes a newer live arena

Finding LFV-037 · Area: store / MMapDirectory · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks. Upstream status: Fixed, by Chris Hegarty (ChrisHegarty; Lucene PMC; wrote and merged PR #16931 and its 10.6.1 backport #16945; triaged and approved by Uwe Schindler (uschindler; Lucene PMC), who cherry-picked the fix to branch_11x) on 2026-10-29 (https://github.com/apache/lucene/pull/16931). Uwe Schindler said it looks very relevant: Uwe had long felt something was wrong with shared arenas, and Gianluca Graziadei had privately reported the same issue (Uwe on #16931, Chris Hegarty agreeing; Uwe's first dev@ message said closely related). Chris Hegarty fixed it in #16931 (merged 2026-10-29; Uwe Schindler cherry-picked it to branch_11x) and backported it for 10.6.1 in #16945. On #16931 Uwe Schindler noted that a single thread reproduces it and that it breaks nothing (no unclosed arenas, only some slowdown after many NRT reopens), so 10.6.0 was not respun for it. This site files nothing upstream.

Fixed upstream: [#16931](https://github.com/apache/lucene/pull/16931) (commit [858c0175e855](https://github.com/apache/lucene/commit/858c0175e855afc5fc1057c15d90ad4dbb8634d0)), merged 2026-10-29; also on branch_11x ([603af8703753](https://github.com/apache/lucene/commit/603af8703753)) and branch_10_6 ([6dde6c73bd1c](https://github.com/apache/lucene/commit/6dde6c73bd1c)).

## What it shows

You use MMapDirectory with its default shared-arena grouping, and the files of one segment are opened more often than an arena's permits while a file opened early is still open (one thread is enough, as Uwe Schindler pointed out on #16931). You expect later files of the group to share the newer arena that took over. Instead the old arena's onClose removes the newer live arena's map entry when the old arena closes, so grouping is lost (577 arenas instead of 10 in a sliding open/close repro).

The test shows that in a default MMapDirectory, closing a group's old arena (A1, permits used up) unregisters the newer arena A2 that is still open, so the next open of the same segment gets a third arena (`TestMMapDirectoryArenaGrouping#testOpenAfterOldArenaClosedSharesNewerArena`, one file, single thread).

## Expected vs actual

Expected: the open after A1 closed shares the open arena A2 (63 permits left). Actual: it gets a new arena; fails with -ea and -da (performance only).

## Files

- [src/org/apache/lucene/store/TestMMapDirectoryArenaGrouping.java](src/org/apache/lucene/store/TestMMapDirectoryArenaGrouping.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (a commit of 2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
- This finding was re-run on Lucene main 82cf7465bb8f on 2026-11-26: it no longer reproduces there; in the commands below, V=12.0.0-SNAPSHOT on a build of main 82cf7465bb8f.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
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
     execute --select-class org.apache.lucene.store.TestMMapDirectoryArenaGrouping
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
