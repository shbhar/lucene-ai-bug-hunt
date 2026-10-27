# When IndexWriter#addBatch rejects a vector part-way through a DENSE vector column, the core HNSW and scalar-quantized vectors writers fall out of step with a flat field writer that keeps the default addDenseValues (new in #16734): the next commit closes the IndexWriter, or with assertions off commits a corrupt HNSW graph or misaligned COSINE quantization state

Bug LFV-579 · Area: codecs / vectors writers (Lucene99HnswVectorsWriter and Lucene104ScalarQuantizedVectorsWriter field writers over a flat field writer; columnar addBatch DENSE vector bulk path) · Priority: medium · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-579.html](../../bugs/LFV-579.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index vectors with IndexWriter#addBatch and a DENSE VectorColumn into an HNSW or scalar-quantized format whose per-field flat storage is your own FlatFieldVectorsWriter (plugged in through the public Lucene99FlatVectorsWriter strategyFactory constructor, without overriding addDenseValues), and Lucene rejects the third vector of a 3-document batch (NaN, or a zero vector with COSINE). You expect what the addDenseValues javadoc and Lucene's own recovery test promise: the batch's documents are deleted, the writer stays open and consistent, and the next commit writes a readable, searchable segment. Instead the flat writer has kept the batch's first two vectors while the HNSW wrapper added no graph nodes for them (and the scalar-quantized wrapper no centroid sums or COSINE magnitudes), so with assertions on the next commit fails ('Level 0 expects to have all nodes', or an assertion in the quantizer) and closes the writer, losing every uncommitted document, and with assertions off it commits an HNSW graph that cannot be read back (EOFException, CheckIndex at level 2 or more fails, a kNN query for a document's own vector returns another document), or the COSINE quantized writer throws ArrayIndexOutOfBoundsException at commit or commits NaN codes so that kNN finds no document of the segment.

## Expected vs actual

- Expected: Every commit succeeds, CheckIndex is clean and the live documents are searchable, as in the control (the same rows as a SPARSE column, or the stock formats).
- Actual: 3 of 4 tests fail: the commit throws `AssertionError: Level 0 expects to have all nodes` (HNSW) or an `AssertionError` in the quantizer (COSINE SQ); with assertions off CheckIndex throws `EOFException` or the commit throws `ArrayIndexOutOfBoundsException`.

## Files

- [src/org/apache/lucene/codecs/lucene99/TestDenseBatchWrapperDelegateDesync.java](src/org/apache/lucene/codecs/lucene99/TestDenseBatchWrapperDelegateDesync.java) (JUnit test)
- [src/org/apache/lucene/sandbox/codecs/dedup/TestDedupHnswDenseBatchFailure.java](src/org/apache/lucene/sandbox/codecs/dedup/TestDedupHnswDenseBatchFailure.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:$LUCENE/lucene/sandbox)/build/libs/lucene-sandbox)-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.lucene99.TestDenseBatchWrapperDelegateDesync \
       --select-class org.apache.lucene.sandbox.codecs.dedup.TestDedupHnswDenseBatchFailure
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestDenseBatchCustomFlatFieldWriter.java](TestDenseBatchCustomFlatFieldWriter.java)
- [TestDenseBatchSearchProbe.java](TestDenseBatchSearchProbe.java)

Formal models and scripts this bug's part of the hunt used: [../../models/upstream-delta-columnar-dense-vector-batch/](../../models/upstream-delta-columnar-dense-vector-batch/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
