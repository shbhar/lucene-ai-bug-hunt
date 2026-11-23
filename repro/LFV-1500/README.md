# DirectIODirectory's output ignores the byte count that FileChannel.write returns, so a short write in the output's final flush (produced with a process file-size limit) leaves a truncated file without any exception; a forced merge then commits the truncated file, deletes the source segments, and the index no longer opens (since 9.0.0)

Finding LFV-1500 · Area: misc / store: DirectIODirectory (direct I/O for merge files): the output's flush to the file channel · Priority: low · Impact: index unusable · Novelty: new

Finding page: [../../bugs/LFV-1500.html](../../bugs/LFV-1500.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You merge an index through DirectIODirectory (an opt-in, experimental directory of the misc module that uses direct I/O for merges) in a process that has a file-size limit (ulimit -f), and the limit falls inside the last buffer of a merged file. You expect what FSDirectory does under the same limit: the merge fails with 'File too large' and the index stays as it was. Instead the operating system writes only the bytes up to the limit, the output drops the count that FileChannel.write returns, close() succeeds on a file that is too short, IndexWriter commits the merged segment and deletes its sources, and the index no longer opens (CorruptIndexException 'codec footer mismatch (file truncated?)').

## Expected vs actual

- Expected: `close()` throws an `IOException`: a file limited to 4,096 bytes cannot hold the 4,097 bytes written.
- Actual: `close()` returns normally (`Expected exception IOException but no exception was thrown` at line 64); the file holds 4,096 of the 4,097 bytes.

## Files

- [src/org/apache/lucene/misc/store/TestDirectIOOutputShortWrite.java](src/org/apache/lucene/misc/store/TestDirectIOOutputShortWrite.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): still reproduces.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced.
- What the test does: it opens a DirectIODirectory (default constructor) over an FSDirectory on a wrapped file system that lets no file grow past one block: the positional write that crosses that limit stores the bytes up to it and returns that count, and a write that starts at or past the limit throws 'File too large', as FileChannel.write does under a process file-size limit. It creates an output with a merge context, writes one block plus one byte and expects close() to throw an IOException, because the last byte cannot be stored. close() returns normally, so the test fails with 'Expected exception IOException but no exception was thrown', the same with and without assertions. Only that message is the finding. The limit is simulated by the test's file system; the forced merge under a real ulimit -f limit that the summary describes is not part of this test. The test opens its file with direct I/O under the JVM's temporary directory, so that directory must be on a disk-backed file system that supports direct I/O (ext4 or xfs, for example). Where it does not (a tmpfs /tmp on Linux 6.1, for example) the test fails earlier and for another reason, with 'FileSystemException: ... Invalid argument', which is not the finding: add -Djava.io.tmpdir=/path/on/such/a/file/system to the java command.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed 6A7E5501
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=6A7E5501 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.store.TestDirectIOOutputShortWrite
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestDirectIOOutputShortWrite.java](TestDirectIOOutputShortWrite.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
