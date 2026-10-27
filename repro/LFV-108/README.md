# If merge setup throws, the vectors merge instances obtained so far never get finishMerge (MergeState constructor and PerFieldKnnVectorsFormat merge copy constructor)

Bug LFV-108 · Area: index / merge setup (MergeState merge instances, KnnVectorsReader finishMerge lifecycle) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You merge segments that have vectors (forceMerge, a background merge or addIndexes(CodecReader...)) and the merge fails while it is being set up, after it already took a vectors merge instance from one source segment or one vector field, for example because re-opening a data file for sequential reads throws an IOException. You expect every merge instance obtained with KnnVectorsReader#getMergeInstance to get its finishMerge call on this failure too, as #14977 ('always called regardless of merge outcome'), the 10.6.0 CHANGES entry for GITHUB#14930 ('including exceptional exits') and the test framework's AssertingKnnVectorsReader close check (mergeInstanceCount == finishMergeCount) say. Instead the failure happens inside new SegmentMerger(...) (the MergeState constructor) or inside the PerFieldKnnVectorsFormat merge copy constructor, before IndexWriter enters the try/finally that calls cleanupMerge, so the instances already obtained never get finishMerge (obtained=1, finished=0); stock formats on main see no effect because their finishMerge does nothing.

## Expected vs actual

- Expected: You expect every merge instance obtained with KnnVectorsReader#getMergeInstance to get its finishMerge call on this failure too, as #14977 ('always called regardless of merge outcome'), the 10.6.0 CHANGES entry for GITHUB#14930 ('including exceptional exits') and the test framework's AssertingKnnVectorsReader close check (mergeInstanceCount == finishMergeCount) say.
- Actual: The failure happens inside new SegmentMerger(...) (the MergeState constructor) or inside the PerFieldKnnVectorsFormat merge copy constructor, before IndexWriter enters the try/finally that calls cleanupMerge, so the instances already obtained never get finishMerge (obtained=1, finished=0); stock formats on main see no effect because their finishMerge does nothing.

## Files

- [src/org/apache/lucene/index/TestMergeInstanceSetupFailure.java](src/org/apache/lucene/index/TestMergeInstanceSetupFailure.java) (JUnit test)
- [src/org/apache/lucene/index/TestPerFieldMergeInstanceSetupFailure.java](src/org/apache/lucene/index/TestPerFieldMergeInstanceSetupFailure.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
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
     execute --select-class org.apache.lucene.index.TestMergeInstanceSetupFailure \
       --select-class org.apache.lucene.index.TestPerFieldMergeInstanceSetupFailure
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
