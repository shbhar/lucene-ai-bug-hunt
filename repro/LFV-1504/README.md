# FaissKnnVectorsFormat (sandbox, new in 11.0): an exception thrown while the Faiss library writes or reads an index through Lucene's IndexOutput or IndexInput (a write the disk refuses at flush or merge, a merge aborted by IndexWriter.rollback(), a read error at segment open) leaves the native upcall and the JVM exits with code 1 instead of the exception reaching the caller

Finding LFV-1504 · Area: sandbox / codecs: FaissKnnVectorsFormat (the Faiss-based vector format: @lucene.experimental, needs the Faiss C library; added by #14178, 11.0.0 would be its first release): the two I/O callbacks through which the library writes and reads an index (FaissLibraryNativeImpl.writeBytes and readBytes, the targets of its upcall stubs) · Priority: low · Impact: crash · Novelty: new

Finding page: [../../bugs/LFV-1504.html](../../bugs/LFV-1504.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index vectors with FaissKnnVectorsFormat (a sandbox, experimental vector format that is new in 11.0 and needs the Faiss native library), and the Directory throws while the library is writing or reading a segment's index: the disk refuses a write during a flush or a merge, IndexWriter.rollback() aborts a running merge, or a read fails while a reader opens. You expect the exception to reach your call (commit, rollback, DirectoryReader.open), as in the control runs with Lucene99HnswVectorsFormat, where commit() throws the IOException and rollback() during a merge returns normally. Instead the JVM prints the exception's stack trace and a line that ends 'The VM will now exit', and the whole process exits with code 1, because the format's read and write callbacks are called from native code and let the exception escape.

## Expected vs actual

- Expected: `commit()` throws the directory's `IOException` and the child JVM ends with exit code 0.
- Actual: The child JVM prints the `IOException` (frame `FaissLibraryNativeImpl.writeBytes:79`), then `Unrecoverable uncaught exception encountered. The VM will now exit`, and exits with code 1 (`expected:<0> but was:<1>` at line 69).

## Files

- [src/org/apache/lucene/sandbox/codecs/faiss/TestFaissWriteFailure.java](src/org/apache/lucene/sandbox/codecs/faiss/TestFaissWriteFailure.java) (JUnit test)

## Run it

- Tested on Lucene main f2b061406c4b (a commit of 2026-11-19): still reproduces.
- Tested on Lucene branch_11x daa54f2e0cd3 (a commit of 2026-11-13): reproduced.
- What the test does: its main method opens an IndexWriter with FaissKnnVectorsFormat on an in-memory Directory whose outputs throw IOException('no space left on device') from writeBytes on the Faiss data file (.faissd) once a flag is set, adds one document with a two-dimensional vector, sets the flag and expects commit() to throw. The test method runs that main method in a child JVM (the same assertion setting, library path and class path) and asserts that the child's exit code is 0. It fails with 'exit code of the child JVM, which printed: ...', then the child's output (the stack trace of java.io.IOException: no space left on device with the frame FaissLibraryNativeImpl.writeBytes, and the JVM's line 'Unrecoverable uncaught exception encountered. The VM will now exit') and 'expected:&lt;0&gt; but was:&lt;1&gt;', the same with and without assertions: the child JVM exited with code 1 where commit() should have thrown. Only the child JVM exits; the JVM that runs the test stays up and reports the failure. Do not call the main method in a JVM that other work shares: it ends that JVM. The test needs the Faiss C library (libfaiss_c 1.11.0 with the libraries it depends on), which this site does not ship. Install it as the Javadoc of the package org.apache.lucene.sandbox.codecs.faiss describes and, on Linux, put its directory on LD_LIBRARY_PATH before the commands (export LD_LIBRARY_PATH=/path/to/faiss/lib) or add -Djava.library.path=/path/to/faiss/lib to the java command; nothing else is needed. Without the library the test class is skipped: the launcher prints 'Dependencies present' beside the class name, '1 tests skipped' and '0 tests failed', and exits with 0. A skip is not a pass and says nothing about the finding. A failure whose printed child output has no 'The VM will now exit' line is not this finding either. On a branch_11x checkout set V=11.0.0-SNAPSHOT. Do not run this test through Lucene's Gradle build: there the child JVM, which gets the test JVM's java.class.path, cannot load the test framework's classes, and the test fails with 'Could not find or load main class' (NoClassDefFoundError: org/apache/lucene/tests/util/LuceneTestCase), which is not this finding; use the commands below.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.sandbox.codecs.faiss.TestFaissWriteFailure
```

## Files shown on the finding page

- [TestFaissWriteFailure.java](TestFaissWriteFailure.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
