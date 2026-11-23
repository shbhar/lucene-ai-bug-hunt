# Concurrent doc-values updates can make a merge throw NullPointerException and close the writer

Finding LFV-013 · Area: index / doc-values updates · Priority: medium · Impact: crash · Novelty: new

Finding page: [../../bugs/LFV-013.html](../../bugs/LFV-013.html)

AI-assisted finding, confirmed by automated checks. Upstream status: Fixed, by Jim Ferenczi (Lucene PMC; PR #16989, merged 2026-11-09) on 2026-11-09 (https://github.com/apache/lucene/pull/16989). Jim Ferenczi fixed it in PR #16989 (opened 2026-11-06, approved by kkewwei on 2026-11-07, merged into main on 2026-11-09 as fa859ba65f6) and cherry-picked the fix to branch_11x and branch_10_6 the same day. ReadersAndUpdates now registers a doc-values-update field that is new to a segment only when one of its pending updates applies in this generation. The PR says the bug was found via this list (#16937) and names LFV-013. It adds our minimal repro as TestMergeDuringUnfinishedDocValuesUpdate, with added checks that the merged index keeps both updates. Our repro passes on main 781025d6fe32 with and without assertions and fails again when only the fix is reverted. The other update kinds, soft deletes, a reader opened during the update, addIndexes of that reader and a background merge are fixed too. First fixed releases: 10.6.1 and 11.0.0, both unreleased as of 2026-11-14. This site files nothing upstream.

Fixed upstream: [#16989](https://github.com/apache/lucene/pull/16989) (commit [fa859ba65f60](https://github.com/apache/lucene/commit/fa859ba65f605856c39991bbb37016cf37110448)), merged 2026-11-09; also on the 11.0 release branch ([8925978b8769](https://github.com/apache/lucene/commit/8925978b8769)) and the 10.6 branch ([4c8e0e4b8545](https://github.com/apache/lucene/commit/4c8e0e4b8545)).

## What it shows

You update a doc-values field that is new to a segment while that segment is being merged. You expect the merge to finish. Instead writeFieldUpdates records the not-yet-applicable field as NUMERIC with dvGen=-1, the merge throws NullPointerException in DocIDMerger, and the IndexWriter closes tragically.

The test shows that a merge of `_0` and `_1` that starts while another thread is applying an `updateBinaryDocValue` of a field that neither segment has (held at one InfoStream message) throws NullPointerException, and the IndexWriter closes.

## Expected vs actual

Expected `maybeMerge()` to succeed; actual: `NullPointerException ... "this.current" is null` at `DocIDMerger$SequentialDocIDMerger.next`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/index/TestMergeDuringUnfinishedDocValuesUpdate.java](src/org/apache/lucene/index/TestMergeDuringUnfinishedDocValuesUpdate.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (a commit of 2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
- This finding was re-run on Lucene main 11c8b6ea99ae on 2026-11-23: it no longer reproduces there; in the commands below, V=12.0.0-SNAPSHOT on a build of main 11c8b6ea99ae.
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
     execute --select-class org.apache.lucene.index.TestMergeDuringUnfinishedDocValuesUpdate
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
