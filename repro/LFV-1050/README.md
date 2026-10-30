# With an index sort and a parent field, a document that IndexWriter rejects (a documented, non-aborting IllegalArgumentException) as the last document of an in-memory segment that holds a document block makes the next flush throw ArrayIndexOutOfBoundsException 'Index 2147483647' and close IndexWriter, losing every uncommitted document (since 9.10.0)

Bug LFV-1050 · Area: index / index sorting with document blocks (IndexingChain flush-time sort, DocumentsWriterPerThread non-aborting rejection) · Priority: medium · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure an index sort and a parent field, add a block (a child and its parent) with addDocuments, then add one document that IndexWriter rejects with IllegalArgumentException because a field's doc-values type conflicts with the block's, and call commit. You expect the rejected document simply not to be added and the commit to keep the two block documents, as it does without a parent field (upstream test testFlushAfterInconsistentSchema). Instead commit throws ArrayIndexOutOfBoundsException 'Index 2147483647 out of bounds for length 3' from the flush-time sort, IndexWriter closes itself and both block documents are lost.

## Expected vs actual

- Expected: You expect the rejected document simply not to be added and the commit to keep the two block documents, as it does without a parent field (upstream test testFlushAfterInconsistentSchema).
- Actual: commit throws ArrayIndexOutOfBoundsException 'Index 2147483647 out of bounds for length 3' from the flush-time sort, IndexWriter closes itself and both block documents are lost.

## Files

- [src/org/apache/lucene/index/TestIndexSortRejectedLastDocument.java](src/org/apache/lucene/index/TestIndexSortRejectedLastDocument.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestIndexSortRejectedLastDocument
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
