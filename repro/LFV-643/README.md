# IndexWriter.updateBinaryDocValue and updateDocValues(Term, BinaryDocValuesField) keep the caller's BytesRef without copying it, so documents still buffered in RAM get the bytes that BytesRef holds at the writer's next document or flush instead of the value passed to the call (every release since 4.8.0)

Bug LFV-643 · Area: index / IndexWriter doc-values updates (BinaryDocValuesUpdate, DocumentsWriterDeleteQueue slices, FieldUpdatesBuffer) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-643.html](../../bugs/LFV-643.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call IndexWriter.updateBinaryDocValue(term, field, value), or updateDocValues(term, binaryDocValuesField), and afterwards reuse or change that BytesRef, for example one BytesRefBuilder scratch for a loop of updates or a BinaryDocValues.binaryValue taken from another reader; each document should get the value it was passed. Documents in flushed segments do, but documents still buffered in RAM get whatever bytes the BytesRef holds when the writer next adds a document or flushes, so a loop over one scratch buffer gives every buffered target the last value. The wrong value is committed silently: no exception, CheckIndex is clean, and the outcome depends on whether the target document had already been flushed.

## Expected vs actual

- Expected: Every document reads the value passed to its update: b and b; new0, new1, new2; src0, src1, src2; x1, x2.
- Actual: 4 of 5 tests fail: buffered=c (flushed=b); new2, new2, new2; src2, src2, src2; x2, x2. The fresh-BytesRef control passes.

## Files

- [src/org/apache/lucene/index/TestBinaryDocValuesUpdateRetainsCallerBytes.java](src/org/apache/lucene/index/TestBinaryDocValuesUpdateRetainsCallerBytes.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestBinaryDocValuesUpdateRetainsCallerBytes
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestBinaryDocValuesUpdateRetainsCallerBytes.java](TestBinaryDocValuesUpdateRetainsCallerBytes.java)
- [optionA-fix-with-test.diff](optionA-fix-with-test.diff)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
