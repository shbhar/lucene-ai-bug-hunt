# MultiReader.close() leaves later sub-readers open when closing an earlier sub-reader throws an unchecked exception: doClose catches only IOException (every release since 1.4)

Finding LFV-1296 · Area: core / index: MultiReader close (the same loop in ParallelLeafReader and ParallelCompositeReader) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1296.html](../../bugs/LFV-1296.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap two DirectoryReaders in a MultiReader (the one-argument constructor closes its sub-readers on close) and the first reader has a reader-closed listener that throws an unchecked exception such as IllegalStateException. You expect MultiReader.close() to close both sub-readers and rethrow the first failure, which is what it does when the failure is an IOException and what IndexReader's own close does under unchecked exceptions. Instead doClose catches only IOException, so the loop stops after the first reader: the second reader is never closed (its refCount stays 1), and because the MultiReader already counts as closed, it can no longer be closed through the MultiReader.

## Expected vs actual

- Expected: `close()` throws the `IllegalStateException` and both readers are closed: `r2.getRefCount()` is 0, as with the fix.
- Actual: `AssertionError: expected:<0> but was:<1>`: `r2` was never closed.

## Files

- [src/org/apache/lucene/index/TestMultiReaderClose.java](src/org/apache/lucene/index/TestMultiReaderClose.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestMultiReaderClose
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestMultiReaderClose.java](TestMultiReaderClose.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
