# Opening a segment whose stored-fields (.fdm) or term-vectors (.tvm) meta file has a damaged slice pointer reports CorruptIndexException but leaves the .fdx (.tvx) file open, because FieldsIndexReader's constructor closes its input only when the header or checksum step fails

Finding LFV-1231 · Area: codecs / stored fields and term vectors (Lucene90 compressing readers: FieldsIndexReader constructor, cleanup when opening a segment fails) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1231.html](../../bugs/LFV-1231.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open an index with DirectoryReader.open, and one segment's stored-fields meta file (.fdm), or term-vectors meta file (.tvm), has a damaged byte in one of the pointers that say where the index data sits in the .fdx (.tvx) file, as a disk or copy error can leave it. You expect what Lucene does for other damaged files: the open fails with CorruptIndexException and every file it opened is closed again. The CorruptIndexException does come, but the .fdx (.tvx) file stays open, because FieldsIndexReader's constructor closes the file it opened only when the header or checksum check fails, not when the slice and DirectMonotonicReader setup right after it throws, and the enclosing reader never received the object, so it cannot close it either.

## Expected vs actual

- Expected: `DirectoryReader.open` throws `CorruptIndexException`, and `dir.close()` then succeeds: no file left open.
- Actual: `CorruptIndexException` as expected, but `dir.close()` fails: `MockDirectoryWrapper: cannot close: there are still 1 open files: {_0.fdx=1}`, opened at `FieldsIndexReader.<init>(FieldsIndexReader.java:72)`.

## Files

- [src/org/apache/lucene/codecs/lucene90/compressing/TestFieldsIndexReaderCorruptMeta.java](src/org/apache/lucene/codecs/lucene90/compressing/TestFieldsIndexReaderCorruptMeta.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.lucene90.compressing.TestFieldsIndexReaderCorruptMeta
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFieldsIndexReaderCorruptMeta.java](TestFieldsIndexReaderCorruptMeta.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
