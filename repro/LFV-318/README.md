# A reordered addIndexes(CodecReader...) silently drops the stored fields and term vectors of the first document after an empty (maxDoc 0) input reader: SlowCompositeCodecReaderWrapper.docIdToReaderId keeps an Arrays.binarySearch hit on a repeated doc start and reads that document from the empty reader

Bug LFV-318 · Area: index / addIndexes reorder path: SlowCompositeCodecReaderWrapper doc-ID-to-reader lookup (stored fields, term vectors), reached through misc BPReorderingMergePolicy or any OneMerge#reorder · Priority: low · Impact: data corruption · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call IndexWriter.addIndexes(CodecReader...) with a 3-document reader, an empty reader (maxDoc == 0) and a 2-document reader on a writer whose merge policy reorders the merge, such as BPReorderingMergePolicy or any OneMerge#reorder that returns a DocMap (the identity is enough), and you expect all 5 documents to keep their stored fields and term vectors, as they do with the same call without reordering. Instead the merged view that IndexWriter builds for the reorder (SlowCompositeCodecReaderWrapper) has doc starts [0, 3, 3, 5], Arrays.binarySearch finds start 3 at the empty reader's index, and docIdToReaderId keeps that exact hit, so merged doc 3 (the first document of the third reader) is read as doc 0 of the empty reader. The new segment is written without any error under -ea and -da: that document is still found by its id term and keeps its doc values, but its stored fields and term vectors are gone.

## Expected vs actual

- Expected: You expect all 5 documents to keep their stored fields and term vectors, as they do with the same call without reordering.
- Actual: The merged view that IndexWriter builds for the reorder (SlowCompositeCodecReaderWrapper) has doc starts [0, 3, 3, 5], Arrays.binarySearch finds start 3 at the empty reader's index, and docIdToReaderId keeps that exact hit, so merged doc 3 (the first document of the third reader) is read as doc 0 of the empty reader.

## Files

- [src/org/apache/lucene/misc/index/TestReorderedAddIndexesWithEmptyReader.java](src/org/apache/lucene/misc/index/TestReorderedAddIndexesWithEmptyReader.java) (JUnit test)
- [src2/org/apache/lucene/misc/index/TestReorderedAddIndexesWithEmptyReader.java](src2/org/apache/lucene/misc/index/TestReorderedAddIndexesWithEmptyReader.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.index.TestReorderedAddIndexesWithEmptyReader
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.index.TestReorderedAddIndexesWithEmptyReader
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/codecreader-wrappers/](../../models/codecreader-wrappers/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
