# If IndexWriter's InfoStream throws while a full in-memory buffer is handed over for flushing, that buffer stays registered as flushing forever, so the writer hangs in its own self-close after an Error, or in close and rollback after an exception (every release since 6.2.0)

Bug LFV-846 · Area: index / IndexWriter flush hand-off (DocumentsWriterFlushControl.checkOutForFlush, updateStallState, waitForFlush) · Priority: low · Impact: hang · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index with IndexWriter's InfoStream turned on, and one logging call throws (an OutOfMemoryError while Lucene builds its 'now stalling flushes' message, or an exception from the logging backend) just as a full in-memory buffer is handed over to be flushed. You expect what the IndexWriter javadoc promises: after an Error the writer closes itself, and after an exception close or rollback still finish. Instead the buffer is already registered as 'flushing' when the log call throws, nothing ever flushes or releases it, and every later wait for running flushes waits forever: after an Error addDocument hangs inside the writer's own rollback, and after an exception close and rollback hang (and, by code reading, commit and an NRT reopen too).

## Expected vs actual

- Expected: You expect what the IndexWriter javadoc promises: after an Error the writer closes itself, and after an exception close or rollback still finish.
- Actual: The buffer is already registered as 'flushing' when the log call throws, nothing ever flushes or releases it, and every later wait for running flushes waits forever: after an Error addDocument hangs inside the writer's own rollback, and after an exception close and rollback hang (and, by code reading, commit and an NRT reopen too).

## Files

- [src/org/apache/lucene/index/TestFlushCheckoutStrandedByLogging.java](src/org/apache/lucene/index/TestFlushCheckoutStrandedByLogging.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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
     execute --select-class org.apache.lucene.index.TestFlushCheckoutStrandedByLogging
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
