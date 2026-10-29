# ParallelLeafReader and ParallelCompositeReader advertise a sub-reader's index sort even when an earlier, unsorted sub-reader serves the sort field, so sorted searches and index-sort range shortcuts return wrong hits and IndexWriter.addIndexes writes a segment that breaks its declared index sort

Bug LFV-981 · Area: core / index (ParallelLeafReader and ParallelCompositeReader index-sort metadata, LeafMetaData.sort; consumers: TopFieldCollector early termination, Sort.getPrimarySortField range shortcuts, IndexWriter.addIndexes(CodecReader...)) · Priority: low · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-981.html](../../bugs/LFV-981.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You keep a main index sorted on a long field s and a sidecar index with the same documents in the same order that re-indexes s with new values (docs 0-3 get 100-103), open them as new ParallelLeafReader(sidecar, main), and expect the reader either to stop claiming the sort or to reject the combination, as it already does for two different sorts, because the first reader that has a field serves it and the sidecar is not sorted. Instead it reports main's sort &lt;long: "s"&gt; in getMetaData while it serves s = 100, 101, 102, 103, 4, 5,...: a top-3 search sorted by s returns docs 0, 1, 2 (s = 100-102) instead of 4, 5, 6, IndexSortSortedNumericDocValuesRangeQuery s:[0 TO 9] counts 10 instead of 6, a range query on a skip-indexed s finds 0 instead of 6, and ParallelCompositeReader behaves the same. IndexWriter.addIndexes(SlowCodecReaderWrapper.wrap(reader)) into an index sorted on s accepts the reader and writes the documents in that order under indexSort=&lt;long: "s"&gt;, a segment that CheckIndex level 2 rejects ('docID=3 sorts after docID=4') and that survives forceMerge.

The test shows that `new ParallelLeafReader(sidecar, main)` advertises main's index sort while the unsorted sidecar serves the sort field, so `IndexWriter.addIndexes` writes a segment that breaks its own index sort.

## Expected vs actual

Expected: `addIndexes` rejects the reader or writes a correctly sorted segment. Actual: CheckIndex level 2 reports `segment has indexSort=<long: "s"> but docID=0 sorts after docID=1`.

## Files

- [src/org/apache/lucene/index/TestParallelLeafReaderShadowedIndexSort.java](src/org/apache/lucene/index/TestParallelLeafReaderShadowedIndexSort.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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
     execute --select-class org.apache.lucene.index.TestParallelLeafReaderShadowedIndexSort
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/parallel-reader-advertises-index-sort-of-shadowed-sort-field/](../../models/parallel-reader-advertises-index-sort-of-shadowed-sort-field/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
