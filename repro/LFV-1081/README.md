# A failed addIndexes(CodecReader...) leaves the incoming fields registered in the writer's field schema, so later valid documents that use those names differently are rejected until the writer is reopened

Finding LFV-1081 · Area: index / IndexWriter addIndexes(CodecReader...) merge (SegmentMerger) and the writer-wide field registry (FieldInfos.FieldNumbers) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1081.html](../../bugs/LFV-1081.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You import an index with IndexWriter.addIndexes(CodecReader...) and the call fails with an IOException (for example a full disk) while the merged segment is written; as its javadoc promises, no documents are added, and the writer stays open. You expect the writer to behave as if the call had never happened, so that a later document may use an imported field name with any schema, as it may on a fresh or reopened writer and after a validation-time failure of the same call. Instead the merge has already registered every incoming field in the writer-wide field schema before it failed, and nothing undoes that: a later document with NumericDocValuesField("f") is rejected ('cannot change field "f" from index options=DOCS to inconsistent index options=NONE') although no document with "f" was ever indexed into this writer, until the writer is reopened. No I/O error is needed: on a writer with an index sort, when the incoming indexes contain document blocks without a parent field, or with a merge policy that returns one merge per incoming reader, two incoming indexes that use a field differently make the call fail with IllegalArgumentException and leave the first index's schema registered in the same way, so a later document, or an import of only the second index, is rejected; before 9.9.0 every conflicting two-reader import did so.

## Expected vs actual

- Expected: After the failed `addIndexes`, the document with `NumericDocValuesField("f", 1)` is accepted, as by a reopened writer.
- Actual: It is rejected: `IllegalArgumentException: cannot change field "f" from index options=DOCS to inconsistent index options=NONE`.

## Files

- [src/org/apache/lucene/index/TestFailedAddIndexesCodecReaderSchema.java](src/org/apache/lucene/index/TestFailedAddIndexesCodecReaderSchema.java) (JUnit test)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): still reproduces.
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
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestFailedAddIndexesCodecReaderSchema
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFailedAddIndexesCodecReaderSchema.java](TestFailedAddIndexesCodecReaderSchema.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
