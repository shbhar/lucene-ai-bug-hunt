# SlowCompositeCodecReaderWrapper's merged vector iterator numbers ordinals by position and ignores each input's own index, so addIndexes of two or more SortingCodecReader inputs through a reordering merge policy writes vectors onto the wrong documents

Bug LFV-278 · Area: index / addIndexes reorder path: SlowCompositeCodecReaderWrapper merged vector values (MergedDocIterator) over SortingCodecReader inputs · Priority: medium · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-278.html](../../bugs/LFV-278.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You re-sort two or more segments with SortingCodecReader and pass them to one IndexWriter.addIndexes(CodecReader...) call on a writer that has no index sort but uses a merge policy that reorders addIndexes merges, such as BPReorderingMergePolicy (an identity OneMerge#reorder is enough), and you expect every document to keep the vector it was indexed with, as it does without the reorder. Instead the merged view that IndexWriter builds for the reorder (SlowCompositeCodecReaderWrapper) numbers vectors by iteration position, while each SortingCodecReader input reports and reads vectors by its original ordinal, so in each input the k-th document in sorted order is written with the vector at original ordinal k. Nothing throws, with or without assertions, CheckIndex reports the new index clean, and kNN search then scores documents by other documents' vectors, in an executed probe including the vector of a document deleted before the call.

## Expected vs actual

- Expected: Every document keeps its own vectors: doc 0 (id 3) holds `[3.0, 97.0]`, as in the no-reorder control.
- Actual: `16 wrong`: doc 0 (id 3) holds id 0's vector `[0.0, 100.0]`, and so on for all 8 documents, float and byte; with id 0 deleted, live id 3 carries its vector. No exception.

## Files

- [src/org/apache/lucene/misc/index/TestReorderedAddIndexesOfSortingReaders.java](src/org/apache/lucene/misc/index/TestReorderedAddIndexesOfSortingReaders.java) (JUnit test)

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

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.index.TestReorderedAddIndexesOfSortingReaders
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestReorderedAddIndexesVectors.java](TestReorderedAddIndexesVectors.java)
- [TestReorderedAddIndexesOfSortingReaders.java](TestReorderedAddIndexesOfSortingReaders.java)
- [merged-iterator-uses-sub-index.diff](merged-iterator-uses-sub-index.diff)

Formal models and scripts this bug's part of the hunt used: [../../models/codecreader-wrappers/](../../models/codecreader-wrappers/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
