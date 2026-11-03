# IOUtils.closeWhileSuppressingExceptions throws IllegalArgumentException 'Self-suppression not permitted' and skips the remaining closeables when a close() throws the very Throwable being handled (for example the JVM's shared OutOfMemoryError), because it calls ex.addSuppressed(e) without an identity check

Finding LFV-1224 · Area: core / util (IOUtils failure-path cleanup helper closeWhileSuppressingExceptions; reached from StandardDirectoryReader.open, the IndexWriter constructor and codec readers and writers) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1224.html](../../bugs/LFV-1224.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

After a failure, Lucene calls IOUtils.closeWhileSuppressingExceptions(ex, closeables...) to close what it had opened and attach any close failures to ex, for example when opening a DirectoryReader fails. If one close() throws the very object ex (the JVM reuses one OutOfMemoryError object once memory is exhausted), you expect what the method documents: every closeable is closed and, for an Error, that same Error is rethrown. Instead ex.addSuppressed(ex) throws IllegalArgumentException 'Self-suppression not permitted', which escapes the loop, so the remaining closeables are never closed and the caller sees the IllegalArgumentException instead of the original failure (kept only as its cause).

## Expected vs actual

- Expected: `second` is closed and the same `OutOfMemoryError` is rethrown.
- Actual: `AssertionError: second closeable not closed`: the call threw `IllegalArgumentException: Self-suppression not permitted` before reaching `second`.

## Files

- [src/org/apache/lucene/util/TestCloseWhileSuppressingSameThrowable.java](src/org/apache/lucene/util/TestCloseWhileSuppressingSameThrowable.java) (JUnit test)

## Run it

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
     execute --select-class org.apache.lucene.util.TestCloseWhileSuppressingSameThrowable
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestCloseWhileSuppressingSameThrowable.java](TestCloseWhileSuppressingSameThrowable.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
