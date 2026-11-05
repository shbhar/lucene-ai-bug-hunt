# InputStreamDataInput.skipBytes calls InputStream.skip once and throws EOFException whenever it skips fewer bytes than asked, but InputStream.skip may legally skip fewer bytes before the end (BufferedInputStream.skip skips only what it has buffered), so skipBytes(n) fails with data left in the stream and the bytes it did skip are lost (since 9.0.0)

Finding LFV-1186 · Area: core / store: InputStreamDataInput (skipBytes) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1186.html](../../bugs/LFV-1186.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap a BufferedInputStream in an InputStreamDataInput, read a byte (which fills the stream's 8 KiB buffer) and call skipBytes(10,000) with 99,999 bytes left. You expect the skip to succeed, as DataInput.skipBytes ('Skip over numBytes bytes') promises and as Lucene 8.x did, and the next readByte() to return byte 10,001. Instead skipBytes calls InputStream.skip once, gets back only the 8,191 buffered bytes (a short count the JDK contract allows before the end of the stream), treats that as end of input and throws EOFException, after those 8,191 bytes have already been consumed; no Lucene code makes this call, so only applications that skip on this public class are affected.

## Expected vs actual

- Expected: `skipBytes(2)` skips bytes 1 and 2, and the next `readByte()` returns 3.
- Actual: `java.io.EOFException` at `InputStreamDataInput.skipBytes(InputStreamDataInput.java:65)` although 3 bytes were left when it was called, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/store/TestInputStreamDataInputSkipBuffered.java](src/org/apache/lucene/store/TestInputStreamDataInputSkipBuffered.java) (JUnit test)

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
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.store.TestInputStreamDataInputSkipBuffered
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestInputStreamDataInputSkipBuffered.java](TestInputStreamDataInputSkipBuffered.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
