# TaskExecutor.invokeAll, which runs concurrent IndexSearcher searches, throws IllegalArgumentException 'Self-suppression not permitted' instead of the tasks' exception, and can return while a started task is still running, when two tasks fail with the same exception object, because IOUtils.useOrSuppress calls addSuppressed without an identity check

Bug LFV-564 · Area: core / search TaskExecutor (concurrent task failure path; util IOUtils.useOrSuppress) · Priority: low · Impact: wrong error · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a concurrent search (an IndexSearcher with an executor, or TaskExecutor.invokeAll directly) and two of its tasks fail with the same exception object, for example because the JVM reuses one preallocated NullPointerException at a hot code site. You expect what the invokeAll javadoc promises: it waits for all tasks to complete and throws the first failure, with later ones added as suppressed exceptions. Instead IOUtils.useOrSuppress calls exc.addSuppressed(exc), the JDK rejects that with IllegalArgumentException('Self-suppression not permitted'), and that exception escapes TaskExecutor.collectResults before the remaining tasks are awaited, so the caller gets the wrong exception type and invokeAll can return while another started task is still running.

## Expected vs actual

- Expected: You expect what the invokeAll javadoc promises: it waits for all tasks to complete and throws the first failure, with later ones added as suppressed exceptions.
- Actual: IOUtils.useOrSuppress calls exc.addSuppressed(exc), the JDK rejects that with IllegalArgumentException('Self-suppression not permitted'), and that exception escapes TaskExecutor.collectResults before the remaining tasks are awaited, so the caller gets the wrong exception type and invokeAll can return while another started task is still running.

## Files

- [src/org/apache/lucene/search/TestTaskExecutorSameExceptionTwice.java](src/org/apache/lucene/search/TestTaskExecutorSameExceptionTwice.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestTaskExecutorSameExceptionTwice
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
