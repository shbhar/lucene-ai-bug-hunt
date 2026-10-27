# A seek or skipBytes far past the end of a small DirectIODirectory slice throws IllegalArgumentException('Negative position') instead of EOFException, because pos + offset overflows

Bug LFV-224 · Area: misc / store: DirectIODirectory (DirectIOIndexInput.seek on slices) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open a file through DirectIODirectory, take a small slice of it at a non-zero offset (for example bytes 100 to 1100 of a 20000-byte file), and seek or skip on the slice to a position far beyond its end, such as Long.MAX_VALUE; IndexInput.seek documents that a position beyond the end throws EOFException, and NIOFSDirectory and MMapDirectory do throw it. DirectIODirectory adds the slice offset to the position without an overflow check, the sum wraps to a negative file position, and the JDK's FileChannel.read rejects it with IllegalArgumentException('Negative position'). The call fails either way and no wrong data is returned; only the exception type is wrong, and only for targets within the slice's offset of Long.MAX_VALUE on slices that lie inside one filesystem block (on larger slices a second overflow happens to produce EOFException).

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/misc/store/TestDirectIOSliceSeekOverflow.java](src/org/apache/lucene/misc/store/TestDirectIOSliceSeekOverflow.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.store.TestDirectIOSliceSeekOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/directio-nrtcaching-dirs/](../../models/directio-nrtcaching-dirs/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
