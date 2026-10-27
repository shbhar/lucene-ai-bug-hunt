# addIndexes of a SortingCodecReader (including the reader that BPIndexReorderer.reorder returns) into a writer that uses BPReorderingMergePolicy with BpVectorReorderer throws UnsupportedOperationException, because SortingCodecReader's vector views do not support copy

Bug LFV-404 · Area: misc / index: BpVectorReorderer merge-time reordering (BPReorderingMergePolicy) over core / index: SortingCodecReader vector views · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You pass a SortingCodecReader, for example the reader that BPIndexReorderer.reorder returns or SortingCodecReader.wrap(reader, docMap, null), to IndexWriter#addIndexes on a writer whose merge policy is BPReorderingMergePolicy with a BpVectorReorderer, and expect the documents to be added and reordered, as they are for plain segment readers and as the same policy does with the postings BPIndexReorderer. Instead addIndexes throws UnsupportedOperationException and adds nothing: the policy reorders addIndexes as a forced merge, BpVectorReorderer's per-thread state calls copy on the input's float vector values, and SortingCodecReader's vector views throw from copy (with several such readers in one call the throw comes through SlowCompositeCodecReaderWrapper's merged copy). Making copy work is not enough on its own: BpVectorReorderer then treats the sorting view's ordinals as doc order, so the reordering is silently applied in the wrong doc space.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: addIndexes throws UnsupportedOperationException and adds nothing: the policy reorders addIndexes as a forced merge, BpVectorReorderer's per-thread state calls copy on the input's float vector values, and SortingCodecReader's vector views throw from copy (with several such readers in one call the throw comes through SlowCompositeCodecReaderWrapper's merged copy).

## Files

- [src/org/apache/lucene/misc/index/TestAddIndexesSortingCodecReaderBpVectorReorder.java](src/org/apache/lucene/misc/index/TestAddIndexesSortingCodecReaderBpVectorReorder.java) (JUnit test)

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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.index.TestAddIndexesSortingCodecReaderBpVectorReorder
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
