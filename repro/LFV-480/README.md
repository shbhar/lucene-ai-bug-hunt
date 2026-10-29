# Inputs from BufferedIndexInput.wrap and the default BufferedIndexInput.slice accept seek and skipBytes past the end instead of throwing EOFException

Bug LFV-480 · Area: store / BufferedIndexInput.SlicedIndexInput (BufferedIndexInput.wrap and the default slice) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You take 20 bytes of a file with BufferedIndexInput.wrap("w", in, 10, 20), or a slice of a BufferedIndexInput subclass that keeps the default slice, call seek(21) or skipBytes(21), and expect the EOFException that IndexInput#seek promises for a position beyond the end and that every stock Directory's inputs and slices throw. Instead the call succeeds and getFilePointer returns 21; only the next read throws EOFException. The cause is the empty SlicedIndexInput.seekInternal: BufferedIndexInput.seek hands every target outside its buffer to seekInternal, and only readInternal checks the slice's length.

The test shows that an input from `BufferedIndexInput.wrap("w", in, 0, 20)` accepts `seek(21)` because `SlicedIndexInput.seekInternal` is empty (the default `BufferedIndexInput#slice` and `skipBytes` reach the same method).

## Expected vs actual

Expected, per the `IndexInput#seek` javadoc: `EOFException` for a position beyond the end. Actual: the seek succeeds (`Expected exception EOFException but no exception was thrown`).

## Files

- [src/org/apache/lucene/store/TestBufferedIndexInputWrapSeekPastEOF.java](src/org/apache/lucene/store/TestBufferedIndexInputWrapSeekPastEOF.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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
     execute --select-class org.apache.lucene.store.TestBufferedIndexInputWrapSeekPastEOF
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
