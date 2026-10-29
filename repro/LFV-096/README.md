# An addBatch that registers a new term-vector field but frames no rows leaves a segment whose FieldInfos declare term vectors with no term-vector files, so the committed index cannot be opened

Bug LFV-096 · Area: index / columnar addBatch (term vectors) · Priority: medium · Impact: index unusable · Novelty: new

Bug page: [../../bugs/LFV-096.html](../../bugs/LFV-096.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add a document, then call IndexWriter#addBatch with a column batch that declares a new text field with term vectors but adds no row: the batch is empty, or it is rejected with IllegalArgumentException because a later column conflicts with the existing schema, or its column cursor throws before the first row. You expect the earlier document to commit and the index to open, as it does when the same rejection happens through addDocument. Instead the segment's FieldInfos say the field has term vectors but no term-vector files were written, so DirectoryReader.open on the commit throws CorruptIndexException ('No sub-file with id.tvd found in compound file'), or, with the default IndexWriterConfig, commit itself throws.

The test shows that an `IndexWriter#addBatch` with an empty batch (`numDocs() == 0`) that declares a new term-vector field, after one `addDocument`, commits a segment whose FieldInfos declare term vectors but that has no term-vector files.

## Expected vs actual

Expected `DirectoryReader.open` to open the commit with 1 document; actual: `CorruptIndexException` caused by `No sub-file with id.tvd found in compound file "_0.cfs"`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/index/TestEmptyColumnBatchTermVectors.java](src/org/apache/lucene/index/TestEmptyColumnBatchTermVectors.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestEmptyColumnBatchTermVectors
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
