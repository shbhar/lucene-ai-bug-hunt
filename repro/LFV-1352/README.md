# ByteBuffersIndexOutput.getChecksum returns 0 for an empty output without asking the supplied Checksum (Adler32 of no bytes is 1), because its size-keyed cache starts out as 'size 0, value 0'

Finding LFV-1352 · Area: store / ByteBuffersIndexOutput (getChecksum with a caller-supplied java.util.zip.Checksum) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1352.html](../../bugs/LFV-1352.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You create a ByteBuffersIndexOutput with the public constructor that takes your own java.util.zip.Checksum, for example new Adler32(), and call getChecksum() before writing anything; you expect Adler32's value for no bytes, which is 1, because IndexOutput.getChecksum returns 'the current checksum of bytes written so far'. Instead you get 0: getChecksum caches its last result by output size, and that cache starts as 'size 0, value 0', so for an empty output it never asks the Checksum. With CRC32, the only Checksum Lucene itself uses, the checksum of no bytes is 0, so the shortcut happens to be right there, and once a byte is written Adler32 values are correct too.

## Expected vs actual

- Expected: `getChecksum()` returns 1, the Adler-32 of no bytes.
- Actual: `java.lang.AssertionError: expected:<1> but was:<0>` at `TestByteBuffersIndexOutputEmptyChecksum.java:29`.

## Files

- [src/org/apache/lucene/store/TestByteBuffersIndexOutputEmptyChecksum.java](src/org/apache/lucene/store/TestByteBuffersIndexOutputEmptyChecksum.java) (JUnit test)

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
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.store.TestByteBuffersIndexOutputEmptyChecksum
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestByteBuffersIndexOutputEmptyChecksum.java](TestByteBuffersIndexOutputEmptyChecksum.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
