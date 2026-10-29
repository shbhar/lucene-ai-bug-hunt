# Lucene90LiveDocsFormat.writeLiveDocs and CheckIndex.bitsCardinality step an int offset by 1024 that wraps to Integer.MIN_VALUE for segments of more than 2,147,482,624 documents, so a deletion in such a segment can never be committed (10.4.0+) and CheckIndex at level 2 or higher reports its valid live docs as an error (10.3.0+)

Bug LFV-147 · Area: codecs / live docs (Lucene90LiveDocsFormat) and index / CheckIndex live-docs check · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index IndexWriter.MAX_DOCS (2,147,483,519) documents into one segment with the default IndexWriterConfig and codec, delete one of them by term and call commit; you expect the deletion to be saved. Instead commit throws 'ArrayIndexOutOfBoundsException: Index -3 out of bounds for length 16' ('AssertionError: startIndex=-129, numBits=1024' with -ea) and close throws it again, because Lucene90LiveDocsFormat.writeBits copies the live docs in 1024-bit batches with 'for (int offset = 0; offset &lt; bits.length; offset += copy.length)': after the batch at 2^31-1024 the offset wraps to Integer.MIN_VALUE, which is still below the length, so the next batch clears a negative range. CheckIndex.bitsCardinality has the same loop, so on 10.3.0 and later CheckIndex (level 2 or higher) also reports the valid live docs of such a segment as an error; only segments with more than 2,147,482,624 documents are affected.

The test shows that `Lucene90LiveDocsFormat.writeLiveDocs` cannot write the live docs of a legal segment with `maxDoc = IndexWriter.MAX_DOCS`: its `int offset += 1024` batch loop wraps to `Integer.MIN_VALUE` after the batch at 2^31-1024, so a deletion in such a segment can never be committed.

## Expected vs actual

Expected: the `.liv` file is written. Actual: `AssertionError: startIndex=-129, numBits=1024` (-ea) or `ArrayIndexOutOfBoundsException: Index -3 out of bounds for length 16` (-da) from `FixedBitSet.clear` in `writeBits`.

## Files

- [src/org/apache/lucene/codecs/lucene90/TestLiveDocsWriteMaxDocs.java](src/org/apache/lucene/codecs/lucene90/TestLiveDocsWriteMaxDocs.java) (JUnit test)

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
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.lucene90.TestLiveDocsWriteMaxDocs
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/bitsets-livedocs-acceptdocs/](../../models/bitsets-livedocs-acceptdocs/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
