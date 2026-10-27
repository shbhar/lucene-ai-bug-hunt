# Closing a clone or full-length slice of a multi-chunk MMapDirectory input nulls the MemorySegment it shares with the original and other clones, which then fail with NullPointerException

Bug LFV-128 · Area: store / MMapDirectory (MemorySegmentIndexInput clone, slice and close) · Priority: medium · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open a file with MMapDirectory that is larger than one mmap chunk, make a clone (or a slice that covers the whole file), close that clone and keep reading the original; you expect the original and the other clones to be unaffected, as they are for single-chunk files, for partial slices, for other directories and on 10.0.0. Instead the clone shares the original's array of memory segments and close fills that array with nulls, so the original's next access to another chunk throws NullPointerException, from then on the never-closed original reports AlreadyClosedException, and closing it afterwards leaves its arena and the file mapping open. Separately, reading a clone after the original was closed throws NullPointerException where the IndexInput#clone javadoc promises AlreadyClosedException.

## Expected vs actual

- Expected: You expect the original and the other clones to be unaffected, as they are for single-chunk files, for partial slices, for other directories and on 10.0.0.
- Actual: The clone shares the original's array of memory segments and close fills that array with nulls, so the original's next access to another chunk throws NullPointerException, from then on the never-closed original reports AlreadyClosedException, and closing it afterwards leaves its arena and the file mapping open.

## Files

- [src/org/apache/lucene/store/TestMultiChunkCloneClose.java](src/org/apache/lucene/store/TestMultiChunkCloneClose.java) (JUnit test)
- [src2/org/apache/lucene/store/TestMultiChunkCloneClose.java](src2/org/apache/lucene/store/TestMultiChunkCloneClose.java) (JUnit test)

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

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.store.TestMultiChunkCloneClose
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.store.TestMultiChunkCloneClose
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
