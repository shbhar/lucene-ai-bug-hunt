# IndexedDISI returns its jump-table entry count as a signed short, so a sparse doc-values, norms or vector field whose last doc id is &gt;= 2,147,352,576 stores a negative count and every reader ignores the jump table that was written (advance walks block by block; results stay correct)

Bug LFV-169 · Area: codecs / IndexedDISI jump table (presence sets of sparse doc values, norms and vectors) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a segment of more than 2,147,352,576 documents with the default codec, with a doc-values, norms or vector field that some documents lack and that a document near the end of the doc-id range has. You expect the field's index-time jump table (added in 8.0 'for O(1) advance') to be used, so that advance to a far document jumps straight to its 65,536-doc block. Instead IndexedDISI.writeBitSet writes 32,768 or 32,769 table entries but returns their count as a short (-32,768 or -32,767), the codec stores that value, and every reader treats a negative count as 'no jump table', so advance and advanceExact walk the block headers one by one; the documents and values returned stay correct.

The test shows that `IndexedDISI.writeBitSet` returns its jump-table entry count as a `short`, so for a doc set whose last doc is in block 32766 (doc 2,147,352,576 &lt; `IndexWriter.MAX_DOCS`) the count of the 32768 entries it wrote wraps to a negative value, which every reader treats as 'no jump table'.

## Expected vs actual

Expected the documented return value 32768 (`@return the number of jump-table entries`); actual -32768 (`expected:<32768> but was:<-32768>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/codecs/lucene90/TestIndexedDISIJumpTableCount.java](src/org/apache/lucene/codecs/lucene90/TestIndexedDISIJumpTableCount.java) (JUnit test)

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
     execute --select-class org.apache.lucene.codecs.lucene90.TestIndexedDISIJumpTableCount
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/indexeddisi-dv-bulk/](../../models/indexeddisi-dv-bulk/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
