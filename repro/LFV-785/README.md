# MemoryIndex reports a field that indexes no terms (doc values, points, vectors or stored only) as indexed with positions and norms, so IndexWriter.addIndexes(SlowCodecReaderWrapper.wrap(reader)) fails next to the same document, or records the wrong schema in an empty index, after which the writer rejects that document

Bug LFV-785 · Area: memory / MemoryIndex reader FieldInfos (index options and norms of fields that index no terms); seen through the IndexWriter.addIndexes(CodecReader...) schema check and FieldExistsQuery · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a MemoryIndex from a Document with a TextField title and a NumericDocValuesField price=42 (or a StoredField, IntPoint or vector field) and read its FieldInfo for price, or copy the reader into an index with IndexWriter.addIndexes(SlowCodecReaderWrapper.wrap(reader)), and expect what IndexWriter records for the same document: price is not indexed (IndexOptions.NONE, no norms), so the copy fits next to natively indexed copies of the document. Instead MemoryIndex reports price as DOCS_AND_FREQS_AND_POSITIONS with norms although it holds no terms for it, so addIndexes into an index holding the same document throws 'cannot change field "price" from index options=NONE to inconsistent index options=DOCS_AND_FREQS_AND_POSITIONS'. A copy into an empty index succeeds but records price as indexed, and addDocument of the same document then throws the reverse message, also in a new writer session.

The test shows that a `MemoryIndex` built from a document with only `NumericDocValuesField("price", 42)` reports "price" as an indexed field.

## Expected vs actual

Expected: `FieldInfo#getIndexOptions()` is `NONE` ("NONE if the field is not indexed"; IndexWriter records NONE for the same field); actual (-ea and -da): `DOCS_AND_FREQS_AND_POSITIONS`, which is what makes `IndexWriter#addIndexes(SlowCodecReaderWrapper.wrap(reader))` reject the copy.

## Files

- [src/org/apache/lucene/index/memory/TestMemoryIndexDocValuesOnlyFieldInfo.java](src/org/apache/lucene/index/memory/TestMemoryIndexDocValuesOnlyFieldInfo.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.memory.TestMemoryIndexDocValuesOnlyFieldInfo
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
