# ByteBuffersDataInput never checks the last buffer of a multi-buffer list for position() == 0, so a last buffer with position() &gt; 0 is accepted and read from index 0, silently returning the wrong bytes

Finding LFV-1461 · Area: store / ByteBuffersDataInput constructor buffer-list validation (ensureAssumptions) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1461.html](../../bugs/LFV-1461.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You split a 12-byte array into two pages with ByteBuffer.wrap(data, 0, 8) and ByteBuffer.wrap(data, 8, 4) and pass them to new ByteBuffersDataInput(List&lt;ByteBuffer&gt;); the constructor accepts the list and length() is 12. You expect to read bytes 0..11, or an IllegalArgumentException, which the constructor throws for the same buffer in any middle slot ('All buffers except for the first one must have position() == 0'). Instead bytes 8..11 read back as 0, 1, 2, 3 and a read at length() returns a byte instead of EOFException, because the validation loop stops before the last buffer and every read addresses that buffer from index 0 instead of from its position(); no exception or assertion fires.

## Expected vs actual

- Expected: `IllegalArgumentException` from the constructor, or `readBytes` returns [1, 2, 3].
- Actual: The list is accepted and `readBytes` returns [1, 2, 1]: `arrays first differed at element [2]; expected:<3> but was:<1>`.

## Files

- [src/org/apache/lucene/store/TestByteBuffersDataInputLastBufferPosition.java](src/org/apache/lucene/store/TestByteBuffersDataInputLastBufferPosition.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
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
     execute --select-class org.apache.lucene.store.TestByteBuffersDataInputLastBufferPosition
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestByteBuffersDataInputLastBufferPosition.java](TestByteBuffersDataInputLastBufferPosition.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
