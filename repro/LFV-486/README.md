# BPIndexReorderer leaks the forward index's term-ids temp input when opening the start-offsets temp file (or building the ForwardIndex) fails, so a failed BP reordering merge leaves an input on a deleted temp file open after IndexWriter and the Directory are closed

Bug LFV-486 · Area: misc / BP reordering (BPIndexReorderer forward index; merge-time reordering through BPReorderingMergePolicy) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You merge with BPReorderingMergePolicy and a BPIndexReorderer (or call BPIndexReorderer#computeDocMap yourself), and while the reorderer opens the two temp files of its on-disk forward index, the second open (the start-offsets file) fails with an IOException, for example because the process has run out of file descriptors. You expect the reorder and the merge to fail and every temp-file input the reorderer opened to be released, as the class does for its other temp inputs and as IndexWriter#close promises ('Closes all open resources'). Instead the input on the term-ids temp file, opened one statement earlier, is never closed, because the two opens and the ForwardIndex constructor sit outside any try/finally, so it stays open (on MMapDirectory, mapped, keeping the deleted file's disk space) after IndexWriter and the Directory are closed.

## Expected vs actual

- Expected: You expect the reorder and the merge to fail and every temp-file input the reorderer opened to be released, as the class does for its other temp inputs and as IndexWriter#close promises ('Closes all open resources').
- Actual: The input on the term-ids temp file, opened one statement earlier, is never closed, because the two opens and the ForwardIndex constructor sit outside any try/finally, so it stays open (on MMapDirectory, mapped, keeping the deleted file's disk space) after IndexWriter and the Directory are closed.

## Files

- [src/org/apache/lucene/misc/index/TestBPIndexReordererForwardIndexLeak.java](src/org/apache/lucene/misc/index/TestBPIndexReordererForwardIndexLeak.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.index.TestBPIndexReordererForwardIndexLeak
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
