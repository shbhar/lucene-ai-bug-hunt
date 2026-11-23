# IndexWriter.addBatch (and updateDocuments with a ColumnBatch) can throw ArrayIndexOutOfBoundsException for a batch with 39 or more stored or indexed columns when Lucene takes an object reference to be 8 bytes (run: -XX:-UseCompressedOops, -XX:+UseZGC, a runtime without the jdk.management module, a 34 GB heap): IndexingChain.processRowColumns grows three parallel arrays to different lengths

Finding LFV-1579 · Area: core / document column batch indexing: IndexWriter#addBatch and updateDocuments with a ColumnBatch, the row pass of IndexingChain (experimental column API) · Priority: low · Impact: crash · Novelty: new

Finding page: [../../bugs/LFV-1579.html](../../bugs/LFV-1579.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index one document through the experimental column API, IndexWriter.addBatch, with a batch of 39 stored long columns and an untouched IndexWriterConfig, on a 64-bit JVM started with -XX:-UseCompressedOops. You expect the document with its 39 stored fields: the same batch gives it with the JVM's default flags at a 2 GB heap on the full JDK, a batch of 38 such columns gives its document under the flag, the same 39 fields are indexed through addDocument under the flag, and no javadoc of the column API limits the number of columns. Instead addBatch throws java.lang.ArrayIndexOutOfBoundsException (Index 38 out of bounds for length 38) from IndexingChain.processRowColumns before any row is processed, with and without assertions: the row pass keeps one reference array and two int arrays for the stored or indexed columns of a batch and grows them by three separate ArrayUtil.grow calls under a test that reads the reference array's length alone, and when Lucene takes a reference to be 8 bytes the three lengths drift apart (42, 38 and 38), so the 39th such column is written past the end of an int array.

## Expected vs actual

- Expected: One document with 39 stored fields, as the same batch gives with the JVM's default flags at a 2 GB heap on the full JDK.
- Actual: With `-XX:-UseCompressedOops`, `addBatch` throws `java.lang.ArrayIndexOutOfBoundsException: Index 38 out of bounds for length 38` from `IndexingChain.processRowColumns`, with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestColumnBatchThirtyNineRowColumns.java](src/org/apache/lucene/index/TestColumnBatchThirtyNineRowColumns.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): reproduced under -XX:-UseCompressedOops, with assertions on and off.
- Tested on Lucene branch_11x 262e019500ec (a commit of 2026-11-22): reproduced under -XX:-UseCompressedOops, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced under -XX:-UseCompressedOops, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- Not new in 11.0: the test fails the same way with the release jars of 10.5.0, 10.5.1, 10.5.2 and 10.6.0, on builds of branch_11x (262e019500ec and 8e4ee3d41386) and on Lucene main, with and without assertions. What the test does: it indexes one document through IndexWriter.addBatch with a batch of 39 stored long columns, an untouched IndexWriterConfig and a ByteBuffersDirectory, and expects the document to have 39 stored fields. The JVM flag -XX:-UseCompressedOops in the java line is part of the recipe: the row pass of addBatch grows a reference array and two int arrays by separate calls, Lucene sizes a reference array by the size it takes an object reference to have, and the lengths drift apart only when that size is 8 bytes. Under the flag addBatch throws java.lang.ArrayIndexOutOfBoundsException: Index 38 out of bounds for length 38 at IndexingChain.processRowColumns (IndexingChain.java:874 on main and on branch_11x, :880 in 10.6.0 and 10.5.2, :877 in 10.5.1 and 10.5.0), so the test's assertion is not reached. For comparison, the same block without -XX:-UseCompressedOops, that is with compressed references, which the JVM's default flags give at this heap size on the full JDK, reports 1 test successful on each of these builds, with and without assertions. Other runs in which Lucene takes a reference to be 8 bytes: with -XX:+UseZGC in place of the flag the block fails the same way on each of these builds; on a runtime without the jdk.management module (the JDK limited to the modules java.base and java.logging), where the JVM itself still has compressed references (its flag print gives UseCompressedOops = true) but Lucene cannot read the JVM option and assumes 8 bytes, a small program with the same batch throws the same exception on 10.6.0, on branch_11x 262e019500ec and on main b5a4546a5964; and under -XX:-UseCompressedOops the test in its first form (a distinct value in each column and a further assertion on the number of documents) fails on each release from 10.5.0 to 10.6.0. A heap of 34 GB was run on 2026-11-22, with the same test class and the JUnit console launcher but not with the block printed below: in a JVM started with -Xmx34g and with no flag that names the object layout or the garbage collector, the test fails with the same exception on the 10.6.0 release jars, on branch_11x 262e019500ec and on main b5a4546a5964, with and without assertions, and with -Xmx2g in place of -Xmx34g it reports 1 test successful on the three builds. Other heap sizes of 32 GB or more were not run: for them there is only the JDK's flag print (java -Xmx32g -XX:+PrintFlagsFinal -version, and the same line with the other sizes), which shows UseCompressedOops = false at 32, 33, 48, 64, 128 and 512 GB and true at 31 GB. The test's input has no randomness; the test framework draws its environment from the seed. On a branch_11x checkout set V=11.0.0-SNAPSHOT; for a release, set V to its version (V=10.6.0 for the 10.6.0 release) and use the three jars of that release from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework) in place of the three $LUCENE jars. For the Gradle alternative at the end of this page the module is lucene/core and the flag has to be given too: with the test copied to lucene/core/src/test/org/apache/lucene/index/ and -Ptests.jvmargs=-XX:-UseCompressedOops added, it reports 1 test completed, 1 failed, with the same exception, on branch_11x 262e019500ec and on main b5a4546a5964, with -Ptests.asserts=true and with -Ptests.asserts=false; without that property the Gradle build is successful, because Lucene then reads compressed references from the test JVM.
- This finding was re-run on Lucene main 11c8b6ea99ae on 2026-11-23: still reproduces.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.ArrayIndexOutOfBoundsException: Index 38 out of bounds for length 38, thrown in IndexingChain.processRowColumns under the test's IndexWriter.addBatch call; when the flag -XX:-UseCompressedOops is left out, the same run reports 1 test successful.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:-UseCompressedOops -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestColumnBatchThirtyNineRowColumns
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestColumnBatchThirtyNineRowColumns.java](TestColumnBatchThirtyNineRowColumns.java)

Formal models and scripts this finding's part of the hunt used: [../../models/addbatch-row-column-arrays-grown-apart/](../../models/addbatch-row-column-arrays-grown-apart/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
