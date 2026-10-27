# addIndexes(CodecReader) from a reader whose term vectors list a field with zero terms writes a term-vector chunk that the default codec cannot read back, and every later merge that re-encodes the segment closes the IndexWriter

Bug LFV-244 · Area: core / codecs: term vectors (TermVectorsWriter.addAllDocVectors merge copier; Lucene90 compressing term-vector writer and reader), reached through IndexWriter.addIndexes(CodecReader) · Priority: medium · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-244.html](../../bugs/LFV-244.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You copy an index with IndexWriter.addIndexes(CodecReader...) through a reader that removes some terms consistently from the postings and the term vectors (CheckIndex at its strictest level accepts this reader), so one document's term-vector field is left with zero terms. You expect the new segment to read back and merge like any other, as the postings do (the postings writer simply drops the emptied field). Instead the default term-vector format writes per-field AvgCharsPerTerm floats that its own reader does not expect, so when the emptied field stores offsets the other documents of the chunk throw 'offset 0 is invalid' on read, and because the reader lists the emptied field but returns null for it, the next merge that re-encodes the segment fails with 'Wrong number of values added' and closes the IndexWriter, on every retry.

## Expected vs actual

- Expected: doc 1's term vectors read back as `[here, public, text]`, `CheckIndex` is clean, and the delete + commit merge succeeds.
- Actual: doc 1 throws `IOException: offset 0 is invalid`, and the commit's merge throws `Wrong number of values added, expected: 2, got: 1` and closes the IndexWriter.

## Files

- [src/org/apache/lucene/index/TestTermVectorsZeroTermField.java](src/org/apache/lucene/index/TestTermVectorsZeroTermField.java) (JUnit test)
- [src2/org/apache/lucene/index/TestTermVectorsZeroTermField.java](src2/org/apache/lucene/index/TestTermVectorsZeroTermField.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestTermVectorsZeroTermField
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestTermVectorsZeroTermField
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestAddIndexesZeroTermVectorField.java](TestAddIndexesZeroTermVectorField.java)
- [fix-with-test.diff](fix-with-test.diff)

Formal models and scripts this bug's part of the hunt used: [../../models/termvectors-compressing/](../../models/termvectors-compressing/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
