# Slicing an MMapDirectory clone with read advice after its original is closed throws AssertionError instead of AlreadyClosedException (PosixNativeAccess.madvise wraps the closed-arena IllegalStateException in an Error)

Bug LFV-129 · Area: store / MMapDirectory read advice (PosixNativeAccess madvise, MemorySegmentIndexInput slices of closed inputs) · Priority: low · Impact: wrong error · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You clone an MMapDirectory input, close the original, and then slice the clone with an IOContext whose read advice is RANDOM or SEQUENTIAL (for example with setReadAdvice(ADVISE_BY_CONTEXT) and IOContext.DEFAULT.withHints(DataAccessHint.RANDOM)); like reading from the clone, which throws AlreadyClosedException, this should report that the input is closed. Instead the slice call throws java.lang.AssertionError: java.lang.IllegalStateException: Already closed, because PosixNativeAccess.madvise wraps every Throwable of the native posix_madvise call in an AssertionError before MemorySegmentIndexInput.advise can turn the closed-arena IllegalStateException into AlreadyClosedException. It is an explicit throw, not an assert, so it happens with assertions disabled too; no data is read or written wrongly.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The slice call throws java.lang.AssertionError: java.lang.IllegalStateException: Already closed, because PosixNativeAccess.madvise wraps every Throwable of the native posix_madvise call in an AssertionError before MemorySegmentIndexInput.advise can turn the closed-arena IllegalStateException into AlreadyClosedException.

## Files

- [src/org/apache/lucene/store/TestCloneSliceWithAdviceAfterClose.java](src/org/apache/lucene/store/TestCloneSliceWithAdviceAfterClose.java) (JUnit test)

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
     execute --select-class org.apache.lucene.store.TestCloneSliceWithAdviceAfterClose
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
