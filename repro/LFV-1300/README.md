# With an index sort, IndexWriter's RAM accounting leaves out the temporary term-vectors (and stored-fields) writer: SortingTermVectorsConsumer.initTermVectorsWriter and SortingStoredFieldsConsumer.initStoredFieldsWriter never set 'accountable = writer' as the base consumers do, so buffered term vectors are missing from IndexWriter.ramBytesUsed() and from flush-by-RAM decisions (every release since 8.7.0)

Finding LFV-1300 · Area: index / IndexWriter RAM accounting with index sorting (SortingTermVectorsConsumer, SortingStoredFieldsConsumer) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1300.html](../../bugs/LFV-1300.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give IndexWriter an index sort and index documents whose fields store term vectors with positions and offsets. You expect IndexWriter.ramBytesUsed() and flush-by-RAM to count the buffered term vectors as they do without a sort, since the indexing chain sums each consumer's 'accountable', which 'either holds the writer or one that returns null'. Instead the sorting consumers create their temporary writer without assigning it to 'accountable', so it counts as 0: the same 100 documents report 1,086,168 with the sort and 4,261,656 without, and with a small RAM buffer the unsorted writer flushes while the otherwise identical sorted writer does not.

## Expected vs actual

- Expected: For this workload, `ramBytesUsed()` is the same with and without the index sort: 508,384 bytes for both, as with the fix.
- Actual: `AssertionError: sorted=168664 unsorted=508384`: with the sort the buffered term vectors are not counted.

## Files

- [src/org/apache/lucene/index/TestIndexSortTermVectorsRamAccounting.java](src/org/apache/lucene/index/TestIndexSortTermVectorsRamAccounting.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestIndexSortTermVectorsRamAccounting
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestIndexSortTermVectorsRamAccounting.java](TestIndexSortTermVectorsRamAccounting.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
