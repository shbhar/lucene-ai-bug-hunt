# IndexWriter.addIndexes (both overloads) accepts an index without the parent field into a writer configured with IndexWriterConfig#setParentField, although the IndexWriter constructor refuses such an index; the next merge of an added segment with the writer's own segments that hold no document blocks throws IllegalStateException and closes the writer, and after a commit that merge fails again under every writer configuration (since 9.10.0)

Finding LFV-1400 · Area: index / IndexWriter.addIndexes validation vs. IndexWriterConfig#setParentField (document blocks, parent field) and the merge-time parent-field check (FieldInfos.getAndValidateParentField through SlowCompositeCodecReaderWrapper in IndexWriter.mergeMiddle) · Priority: medium · Impact: index unusable · Novelty: new

Finding page: [../../bugs/LFV-1400.html](../../bugs/LFV-1400.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You write a small index with a default IndexWriterConfig, open a second writer configured with setParentField("_parent"), add one document and call addIndexes with the first index. You expect addIndexes either to reject the source with an IllegalArgumentException, as new IndexWriter on that index with the same config does ('can't add a parent field to an already existing index without a parent field'), or the writer to keep working. Instead addIndexes returns normally and the next merge of the two segments (forceMerge(1) in the repro) throws IllegalStateException 'expected parent doc field to be "_parent " across all segments but found a segment with different field "null"', which IndexWriter treats as tragic, so the writer closes and its uncommitted documents are lost.

## Expected vs actual

- Expected: `addIndexes(A)` throws `IllegalArgumentException`, as the constructor does for A, or `forceMerge(1)` succeeds and B stays open.
- Actual: `addIndexes(A)` returns; `forceMerge(1)` throws `IllegalStateException: expected parent doc field to be "_parent " across all segments but found a segment with different field "null"` and B is closed.

## Files

- [src/org/apache/lucene/index/TestAddIndexesWithoutParentField.java](src/org/apache/lucene/index/TestAddIndexesWithoutParentField.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
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
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestAddIndexesWithoutParentField
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestAddIndexesWithoutParentField.java](TestAddIndexesWithoutParentField.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
