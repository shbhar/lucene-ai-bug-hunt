# IndexWriter#addBatch does not validate tuple doc-ids on some doc-values column paths: a doc-values-only SORTED/SORTED_SET DictionaryColumn accepts a doc-id outside [0, numDocs) and writes the value onto a neighbouring document, and SORTED_NUMERIC/SORTED_SET columns check doc-id order only with a Java assert, so decreasing doc-ids close the writer (-ea) or are accepted and break later documents and commits (-da)

Bug LFV-098 · Area: index / columnar addBatch (column pass: DictionaryColumn, SORTED_NUMERIC and SORTED_SET doc-values writers) · Priority: low · Impact: data corruption · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add a batch with IndexWriter#addBatch whose doc-values-only DictionaryColumn (SORTED or SORTED_SET) has a tuple cursor that returns a batch doc-id equal to numDocs or -1, or whose SORTED_NUMERIC or SORTED_SET column returns doc-ids in decreasing order, for example (1, v) then (0, v'). You expect an IllegalArgumentException with the writer still open and no other document touched, which is what the same addBatch call does for LongColumn, BinaryColumn, vector and stored or indexed (row-pass) columns that break the same documented contract. Instead the out-of-range value is silently attached to the document indexed just before or just after the batch (it survives commit, and CheckIndex reports a clean index), and the out-of-order tuples either throw an AssertionError that closes the writer (-ea) or are accepted (-da), after which a later valid addDocument is rejected with 'Out of order doc ids: last=1, next=0', another later document reads a value from the batch, and the next commit can fail and close the writer.

The test shows that IndexWriter#addBatch accepts an out-of-range tuple doc-id (2 when numDocs=2) on a doc-values-only SORTED DictionaryColumn and writes that value onto the next document added to the index.

## Expected vs actual

Expected: the document added after the batch has no "tag" value (or the batch is rejected). Actual: it reads "b" from the batch (`expected null, but was:<b>`, with -ea and -da).

## Files

- [src/org/apache/lucene/document/column/TestDictionaryColumnDocIdOutOfRange.java](src/org/apache/lucene/document/column/TestDictionaryColumnDocIdOutOfRange.java) (JUnit test)

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
     execute --select-class org.apache.lucene.document.column.TestDictionaryColumnDocIdOutOfRange
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
