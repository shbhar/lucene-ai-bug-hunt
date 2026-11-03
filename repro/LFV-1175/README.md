# Lucene90CompressingTermVectorsFormat documents and validates its last argument, blockSize, as a chunk count (&gt;= 1), but the writer uses it as a DirectMonotonic block shift limited to [2, 22], so values such as 1024 or 1 are accepted and the first flush with term vectors throws and closes the IndexWriter (since 8.5.0)

Finding LFV-1175 · Area: core / codecs: compressing term vectors format (Lucene90CompressingTermVectorsFormat blockSize validation; FieldsIndexWriter, DirectMonotonicWriter) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1175.html](../../bugs/LFV-1175.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a custom codec whose term vectors format is new Lucene90CompressingTermVectorsFormat(..., blockSize) and, as its javadoc says ('the number of chunks to store in an index block'), pass a chunk count such as 1024; the constructor accepts it, since it rejects only values below 1. You expect a format that writes and reads term vectors, or an IllegalArgumentException from the constructor, as the stored-fields twin Lucene90CompressingStoredFieldsFormat gives for the same argument. Instead the value goes unchanged to the writer, which uses it as a DirectMonotonicWriter block shift, so addDocument succeeds and the first flush with term vectors (here DirectoryReader.open(writer) or commit) throws 'blockShift must be in [2-22], got 1024' and closes the IndexWriter; blockSize 1, the constructor's own minimum, fails the same way.

## Expected vs actual

- Expected: The constructor throws `IllegalArgumentException`, or the reader opens and shows the document's 2 terms.
- Actual: `DirectoryReader.open(writer)` throws `IllegalArgumentException: blockShift must be in [2-22], got 1024` from `FieldsIndexWriter.finish(FieldsIndexWriter.java:125)`, assertions on and off.

## Files

- [src/org/apache/lucene/codecs/lucene90/compressing/TestTermVectorsBlockSize.java](src/org/apache/lucene/codecs/lucene90/compressing/TestTermVectorsBlockSize.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.lucene90.compressing.TestTermVectorsBlockSize
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestTermVectorsBlockSize.java](TestTermVectorsBlockSize.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
