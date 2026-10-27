# IndexReader#decRef javadoc says 'If an exception is hit, the refCount is unchanged', but since LUCENE-5553 (4.7.1) a final decRef whose doClose or closed listener throws leaves the reader closed at refCount 0 (documentation defect; the code behaviour is intended and must not change)

Bug LFV-497 · Area: index / IndexReader reference counting (decRef javadoc) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

The javadoc of IndexReader#decRef says 'If an exception is hit, the refCount is unchanged', so after a final decRef that throws (for example because a reader-closed listener or the reader's doClose throws IOException) getRefCount should still be 1. Instead the count is 0, the reader is closed, and a retried decRef throws AlreadyClosedException. The code is the intended side: LUCENE-5553 (2014) deliberately made a failing close really close the reader and still notify every listener, and removed the old put-back-on-failure code that the sentence described, so the fix is javadoc-only.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The count is 0, the reader is closed, and a retried decRef throws AlreadyClosedException.

## Files

- [src/org/apache/lucene/index/TestDecRefExceptionRefCount.java](src/org/apache/lucene/index/TestDecRefExceptionRefCount.java) (JUnit test)

## Run it

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
     execute --select-class org.apache.lucene.index.TestDecRefExceptionRefCount
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
