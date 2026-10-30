# DirectoryReader.openIfChanged on a reader opened with a leaf sorter leaks segment reader references when the sorter throws while the new reader is built, so closing the old reader no longer closes the shared segments and the new segment's reader is never closed

Bug LFV-1059 · Area: core / index reader reopen (StandardDirectoryReader reopen factory behind every non-NRT openIfChanged, with a leaf sorter, LUCENE-9507) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open a DirectoryReader with a leaf sorter and later call DirectoryReader.openIfChanged; if the sorter throws while the new reader sorts its leaves, you expect only that exception, because the javadoc makes you responsible just for the old reader and the initial open releases its readers in exactly this case. Instead the reopen factory has already taken a reference on each reused segment reader and opened the new ones, and it builds the new reader with no try/catch, so after the failed call the shared segment reader's refCount is 2 instead of 1 (1 instead of 0 after you close the old reader) and the new segment's reader is never closed.

## Expected vs actual

- Expected: You expect only that exception, because the javadoc makes you responsible just for the old reader and the initial open releases its readers in exactly this case.
- Actual: The reopen factory has already taken a reference on each reused segment reader and opened the new ones, and it builds the new reader with no try/catch, so after the failed call the shared segment reader's refCount is 2 instead of 1 (1 instead of 0 after you close the old reader) and the new segment's reader is never closed.

## Files

- [src/org/apache/lucene/index/TestReopenSorterFailureRefCount.java](src/org/apache/lucene/index/TestReopenSorterFailureRefCount.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestReopenSorterFailureRefCount
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/never-judged-backlog-closure-low-and-not-a-bug/](../../models/never-judged-backlog-closure-low-and-not-a-bug/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
