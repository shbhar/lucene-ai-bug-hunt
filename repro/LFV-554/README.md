# MultiPassIndexSplitter.split never closes the IndexWriter it opened on an output when addIndexes throws, so that output directory stays write-locked and every later IndexWriter or split into it in the same JVM fails with LockObtainFailedException until the JVM restarts

Bug LFV-554 · Area: misc / index tools (MultiPassIndexSplitter.split, failure cleanup) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You split an index with MultiPassIndexSplitter.split, the split throws while it writes one of the output parts (for example the disk fills up, a file of the source index is corrupted, or the source index was created by the previous major version), and you then retry the split or open an IndexWriter on that output directory in the same JVM. You expect this to work once the cause is gone, because the failed split should close the IndexWriter it opened on the output. Instead split calls close only after addIndexes succeeds, so the writer, which the caller cannot reach, keeps the output's write lock, and every later IndexWriter or split into that directory throws LockObtainFailedException 'Lock held by this virtual machine' until the JVM restarts.

## Expected vs actual

- Expected: You expect this to work once the cause is gone, because the failed split should close the IndexWriter it opened on the output.
- Actual: split calls close only after addIndexes succeeds, so the writer, which the caller cannot reach, keeps the output's write lock, and every later IndexWriter or split into that directory throws LockObtainFailedException 'Lock held by this virtual machine' until the JVM restarts.

## Files

- [src/org/apache/lucene/misc/index/TestMultiPassIndexSplitterFailure.java](src/org/apache/lucene/misc/index/TestMultiPassIndexSplitterFailure.java) (JUnit test)

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
     execute --select-class org.apache.lucene.misc.index.TestMultiPassIndexSplitterFailure
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
