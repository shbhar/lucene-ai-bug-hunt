# If the IndexWriter InfoStream throws inside registerMerge, the merge stays queued but its segments are not marked as merging, so the next merge selection picks the same segments again and, with assertions disabled, forceMerge commits duplicated documents (every release since 3.2.0)

Bug LFV-842 · Area: index / IndexWriter merge registration (registerMerge, pendingMerges, mergingSegments) · Priority: medium · Impact: data corruption · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You enable IndexWriter's InfoStream, and the logging call throws once (an exception from the logging backend, or an OutOfMemoryError) while forceMergeDeletes registers its merge; forceMergeDeletes rethrows and the writer stays open, so you expect the index to stay as it was: a following forceMerge(1) and commit should leave the same 10 live documents. But registerMerge had already queued the merge and had not yet marked its segments as merging, so forceMerge(1) selects the same segments again, both merges run, and with -da the committed index holds 16 live documents with id:1 twice. With -ea the next merge fails 'assert merge.registerDone' in IndexWriter._mergeInit instead.

## Expected vs actual

- Expected: You expect the index to stay as it was: a following forceMerge(1) and commit should leave the same 10 live documents.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/index/TestRegisterMergeThrowLeavesHalfRegisteredMerge.java](src/org/apache/lucene/index/TestRegisterMergeThrowLeavesHalfRegisteredMerge.java) (JUnit test)
- [src/org/apache/lucene/index/TestRegisterMergeThrowCms.java](src/org/apache/lucene/index/TestRegisterMergeThrowCms.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestRegisterMergeThrowLeavesHalfRegisteredMerge

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestRegisterMergeThrowCms
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
