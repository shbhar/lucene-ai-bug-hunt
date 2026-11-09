# IndexWriter.addIndexes(CodecReader...) opens its merged segment with an estimated merge size of -1 ('unknown'), which NRTCachingDirectory reads as a tiny merge, so the whole merged segment is cached in heap past maxMergeSizeMB until the next commit, and a large addIndexes can end in OutOfMemoryError with a tragic writer close (every release since 3.2.0; before 5.0.0 through addIndexes(IndexReader...))

Finding LFV-1430 · Area: store / NRTCachingDirectory cache decision for IndexWriter.addIndexes(CodecReader...) outputs (IOContext MergeInfo estimatedMergeBytes -1) · Priority: medium · Impact: crash · Novelty: new

Finding page: [../../bugs/LFV-1430.html](../../bugs/LFV-1430.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap your directory in NRTCachingDirectory with maxMergeSizeMB 1.0 and call IndexWriter.addIndexes(CodecReader...) with the segments of an index of about 2 MB. You expect the merged segment to be written to the wrapped directory, because NRTCachingDirectory caches only flushes and merges whose estimated size is at most maxMergeSizeMB and, since LUCENE-9115, no files of unknown size. Instead IndexWriter opens every output of that merge with a merge size of -1, NRTCachingDirectory compares -1 with its limits and admits it, so all 2.1 MB stay in the heap cache until the next commit; with more data than free heap the merge fails with OutOfMemoryError and the writer closes.

## Expected vs actual

- Expected: At most 1 MiB cached: the 2.1 MB merged segment is above `maxMergeSizeMB` 1.0 and goes to disk.
- Actual: `AssertionError: cached 2107555 bytes`: the whole segment is in the heap cache (assertions on and off; 10.6.0 and 9.12.3 too).

## Files

- [src/org/apache/lucene/store/TestNRTCachingAddIndexes.java](src/org/apache/lucene/store/TestNRTCachingAddIndexes.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.store.TestNRTCachingAddIndexes
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestNRTCachingAddIndexes.java](TestNRTCachingAddIndexes.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
