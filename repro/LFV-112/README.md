# With assertions enabled, re-sorting an index with document blocks through SortingCodecReader and addIndexes fails SortingCodecReader's cache assertion for the parent field

Bug LFV-112 · Area: index / SortingCodecReader re-sort (index sorting with document blocks) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You re-sort an index that uses document blocks and a parent field (IndexWriterConfig#setParentField) the way the SortingCodecReader javadoc describes: wrap each segment with SortingCodecReader.wrap(reader, newSort) and pass the wrapped readers to IndexWriter#addIndexes(CodecReader...) of a fresh writer with the new index sort and the same parent field. You expect the re-sort to succeed, as it does with assertions disabled (the result is then correctly sorted with every block intact) and as it does for an index without blocks. Instead, with Java assertions enabled (-ea), addIndexes throws AssertionError 'only sort fields should be cached twice but [_parent] is not a sort field' and the destination writer closes, because MultiSorter reads the parent field's doc values once to find the blocks and the doc-values merge reads them again, while SortingCodecReader's internal check allows a second read only for sort fields.

## Expected vs actual

- Expected: You expect the re-sort to succeed, as it does with assertions disabled (the result is then correctly sorted with every block intact) and as it does for an index without blocks.
- Actual: With Java assertions enabled (-ea), addIndexes throws AssertionError 'only sort fields should be cached twice but [_parent] is not a sort field' and the destination writer closes, because MultiSorter reads the parent field's doc values once to find the blocks and the doc-values merge reads them again, while SortingCodecReader's internal check allows a second read only for sort fields.

## Files

- [src/org/apache/lucene/index/TestSortingCodecReaderParentFieldAddIndexes.java](src/org/apache/lucene/index/TestSortingCodecReaderParentFieldAddIndexes.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestSortingCodecReaderParentFieldAddIndexes
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
