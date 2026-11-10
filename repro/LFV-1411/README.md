# With assertions enabled, a bulk stored-fields merge checks each copied incomplete (dirty) chunk against the merging format's maxDocsPerChunk instead of the limit it was written with, so merging a segment written with a larger maxDocsPerChunk (same CompressionMode instance and chunk size) throws AssertionError in Lucene90CompressingStoredFieldsWriter.copyChunks and closes the IndexWriter (-ea only; every release since 8.9.0)

Finding LFV-1411 · Area: codecs / stored fields: Lucene90CompressingStoredFieldsWriter bulk merge (copyChunks, getMergeStrategy) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1411.html](../../bugs/LFV-1411.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

An application indexes with a stored-fields format built on the public Lucene90CompressingStoredFieldsFormat, later reopens the index with a format that keeps the same CompressionMode and chunk size but a smaller maxDocsPerChunk, and merges. The merge should succeed: the format forbids only varying the CompressionMode under one name, the writer itself picks a raw chunk-by-chunk (bulk) copy for such a segment, and that copy is correct. Instead, with assertions enabled, copyChunks asserts that each copied incomplete chunk has fewer docs than the merging writer's own limit, which chunks of the older segment need not satisfy, so forceMerge throws AssertionError and the IndexWriter closes; with -da the merge succeeds and all documents read back.

## Expected vs actual

- Expected: `forceMerge(1)` succeeds and the index has one segment with both documents.
- Actual: With `-ea`: `java.lang.AssertionError` at `Lucene90CompressingStoredFieldsWriter.copyChunks:584` and the writer closes; with `-da` the merge succeeds.

## Files

- [src/org/apache/lucene/codecs/lucene90/compressing/TestBulkMergeDirtyChunkMaxDocs.java](src/org/apache/lucene/codecs/lucene90/compressing/TestBulkMergeDirtyChunkMaxDocs.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
- This test fails only with assertions enabled: run the java command below with -ea -esa -Dtests.asserts=true in place of -da -dsa -Dtests.asserts=false. With assertions off it passes, because the bulk copy itself is correct.
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
     execute --select-class org.apache.lucene.codecs.lucene90.compressing.TestBulkMergeDirtyChunkMaxDocs
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestBulkMergeDirtyChunkMaxDocs.java](TestBulkMergeDirtyChunkMaxDocs.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
