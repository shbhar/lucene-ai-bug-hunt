# IndexWriter#addBatch rejects an untokenized BinaryColumn or DictionaryColumn whose FieldType indexes positions or term-vector positions, which addDocument with the same Field type accepts: the column adapter always takes the single-term InvertableType.BINARY path for untokenized values, so the row pass throws 'Fields that are tokenized or index proximity data must produce a non-null TokenStream' after ColumnValidation accepted the type (since 10.5.0)

Finding LFV-1229 · Area: core / document column batch indexing: IndexWriter#addBatch, BinaryColumnAdapter invertable type (experimental column API) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1229.html](../../bugs/LFV-1229.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give an untokenized FieldType DOCS_AND_FREQS_AND_POSITIONS (or term vectors with positions) and index the value "x" through IndexWriter#addBatch with a BinaryColumn or DictionaryColumn, and expect what addDocument(new Field("f", new BytesRef("x"), type)) does with the same type: one token at position 0. Instead addBatch throws IllegalArgumentException 'Fields that are tokenized or index proximity data must produce a non-null TokenStream, but f did not' in its row pass, after ColumnValidation accepted the type. The internal BinaryColumnAdapter picks the single-term InvertableType.BINARY path from tokenized() alone and returns no TokenStream, and IndexingChain.invertTerm allows BINARY only without proximity data, whereas Field always wraps an untokenized value in a one-token stream.

## Expected vs actual

- Expected: `addBatch` indexes term `x` in doc 0 at position 0, as `addDocument` does with the same type.
- Actual: `addBatch` throws `IllegalArgumentException: Fields that are tokenized or index proximity data must produce a non-null TokenStream, but f did not` (`IndexingChain.java:2143`).

## Files

- [src/org/apache/lucene/document/column/TestBinaryColumnUntokenizedPositions.java](src/org/apache/lucene/document/column/TestBinaryColumnUntokenizedPositions.java) (JUnit test)

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
     execute --select-class org.apache.lucene.document.column.TestBinaryColumnUntokenizedPositions
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestBinaryColumnUntokenizedPositions.java](TestBinaryColumnUntokenizedPositions.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
