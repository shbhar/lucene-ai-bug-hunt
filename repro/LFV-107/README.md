# ByteBuffersDataOutput.copyBytes narrows its long length to a signed int, so a copy of 2 GiB or more (for example Directory.copyFrom into a ByteBuffersDirectory) silently copies nothing or only (int) numBytes bytes

Bug LFV-107 · Area: store / ByteBuffersDataOutput · Priority: low · Impact: data corruption · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You copy 2 GiB or more into an in-heap ByteBuffersDataOutput, for example with Directory.copyFrom or IndexWriter.addIndexes(Directory) into a ByteBuffersDirectory. You expect every byte to be copied, as DataOutput.copyBytes(DataInput, long numBytes) promises ('Copy numBytes bytes from input to ourself') and as Lucene 9.2.0 does. Instead the override stores the length in an int, copies max(0, (int) numBytes) bytes (nothing at all for any length from 2^31 to 2^32-1) and returns normally, so the destination file is empty or truncated with no exception; a copied index then fails to open with CorruptIndexException.

## Expected vs actual

- Expected: You expect every byte to be copied, as DataOutput.copyBytes(DataInput, long numBytes) promises ('Copy numBytes bytes from input to ourself') and as Lucene 9.2.0 does.
- Actual: The override stores the length in an int, copies max(0, (int) numBytes) bytes (nothing at all for any length from 2^31 to 2^32-1) and returns normally, so the destination file is empty or truncated with no exception; a copied index then fails to open with CorruptIndexException.

## Files

- [src/org/apache/lucene/store/TestByteBuffersDataOutputCopyBytesOver2GiB.java](src/org/apache/lucene/store/TestByteBuffersDataOutputCopyBytesOver2GiB.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.store.TestByteBuffersDataOutputCopyBytesOver2GiB
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/datainput-impls-strings/](../../models/datainput-impls-strings/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
