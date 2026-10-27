# MemoryIndex returns term vectors for fields whose FieldInfo says storeTermVector=false (a regression of LUCENE-6402 since 6.5.0), so its reader fails CheckIndex and IndexWriter.addIndexes(SlowCodecReaderWrapper.wrap(reader)) writes a segment that CheckIndex rejects

Bug LFV-788 · Area: memory / MemoryIndex reader FieldInfos vs term vectors (consequence in index / addIndexes(CodecReader...) term-vector merge) · Priority: low · Impact: data corruption · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a MemoryIndex with MemoryIndex.fromDocument from a document whose body field stores term vectors and whose title is a plain TextField, copy its reader into an index with IndexWriter.addIndexes(SlowCodecReaderWrapper.wrap(reader)), and expect a clean index, as when the same document is added with IndexWriter.addDocument. Instead the copy succeeds and CheckIndex (level 2 or higher) reports the new segment broken, 'docID=0 has term vectors for field=title but FieldInfo has storeTermVector=false', because the MemoryIndex reader returns term vectors for every field with terms while, since 6.5.0, its FieldInfo takes storeTermVector from the field type. The bad segment survives merges with natively added documents, and the MemoryIndex reader itself already fails TestUtil.checkReader, even for a document that has only a plain TextField.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The copy succeeds and CheckIndex (level 2 or higher) reports the new segment broken, 'docID=0 has term vectors for field=title but FieldInfo has storeTermVector=false', because the MemoryIndex reader returns term vectors for every field with terms while, since 6.5.0, its FieldInfo takes storeTermVector from the field type.

## Files

- [src/org/apache/lucene/index/memory/TestMemoryIndexTermVectorsFieldInfo.java](src/org/apache/lucene/index/memory/TestMemoryIndexTermVectorsFieldInfo.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.memory.TestMemoryIndexTermVectorsFieldInfo
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
