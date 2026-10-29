# A rejected document leaves its doc-values skip index type in the field's per-document schema state, so later valid documents with that field are rejected until the in-memory segment is flushed

Bug LFV-116 · Area: index / IndexingChain per-field schema validation (FieldSchema) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a document in which field f carries a skip-indexed doc-values field (for example SortedDocValuesField.indexedField, new in 10.0), IndexWriter correctly rejects it with IllegalArgumentException because f is already indexed without doc values (or because another field of that document is invalid), and you keep adding documents that have f without doc values. You expect only the bad document to be rejected, as for any non-aborting exception, and the next valid documents to be accepted, as they are when the rejected document used doc values without a skip index. Instead every later document with f and no doc values in the same in-memory segment is rejected ("doc values skip index type: expected 'NONE', but it has 'RANGE'", or "field 'f' cannot have docValuesSkipIndexType=RANGE with doc values type NONE") until the next flush, because IndexingChain.FieldSchema.reset clears the per-document doc-values type but not the skip index type, so the rejected document's RANGE is left behind.

The test shows that after IndexWriter rejects a document that adds `SortedDocValuesField.indexedField("f")` to a postings-only field `f`, the next valid document with only `StringField f` is rejected too, because `FieldSchema.reset()` keeps the rejected document's skip index type.

## Expected vs actual

Expected the third document (same schema as the first) to be accepted; actual: `IllegalArgumentException... doc values skip index type: expected 'NONE', but it has 'RANGE'`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/index/TestRejectedSkipIndexDoc.java](src/org/apache/lucene/index/TestRejectedSkipIndexDoc.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestRejectedSkipIndexDoc
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/indexingchain-schema/](../../models/indexingchain-schema/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
