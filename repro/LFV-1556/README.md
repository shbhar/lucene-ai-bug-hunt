# Under LogByteSizeMergePolicy a forceMerge(1) on an index that is already one merged segment can merge that segment again when its size straddles the compound-file threshold: IndexWriter decides the compound file from the segment's size before packing, and MergePolicy.isMerged asks the same rule again with the larger size of the packed segment; on branch_11x and main (both unreleased) the test's index needs no compound-file setting (default 64 MiB threshold since #15295), and 10.6.0, which leaves an index of the same documents alone, writes it again with setNoCFSRatio(1.0) and setMaxCFSSegmentSizeMB(64) set

Finding LFV-1556 · Area: index / merge policies and the compound-file decision for merged segments (MergePolicy.isMerged, LogMergePolicy, IndexWriter, CompoundFormat's size threshold) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You use LogByteSizeMergePolicy, call forceMerge(1) and commit, and later call forceMerge(1) again with nothing added, updated or deleted. You expect the second call to leave the one merged segment alone, as it did in the runs with a last document about 10 KB shorter or longer and under TieredMergePolicy. In the test the second call merged that one segment into a new one instead: the merged segment is just under 64 MiB before it is packed into a compound file (67,108,504 bytes) and just over afterwards (67,109,225), so the writer packed it, and MergePolicy.isMerged, asking the same size rule again with the packed size, takes the compound segment for unmerged; on branch_11x and main (both unreleased) this needed no compound-file setting, while 10.6.0 left an index of the same documents alone and wrote it again when setNoCFSRatio(1.0) and setMaxCFSSegmentSizeMB(64) were set together.

## Expected vs actual

- Expected: You expect the second call to leave the one merged segment alone, as it did in the runs with a last document about 10 KB shorter or longer and under TieredMergePolicy.
- Actual: While the reported behaviour is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/index/TestForceMergeTwiceAtDefaultCompoundFileThreshold.java](src/org/apache/lucene/index/TestForceMergeTwiceAtDefaultCompoundFileThreshold.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): still reproduces.
- Tested on Lucene main d7162870c1fd (a commit of 2026-11-19): reproduced.
- A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestForceMergeTwiceAtDefaultCompoundFileThreshold
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
