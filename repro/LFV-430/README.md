# If tryDeleteDocument removes a segment's last live document and closing that segment's pooled reader throws, IndexWriter stays open but never counts the change, so NRT readers hide the document while commit and close keep it

Bug LFV-430 · Area: index / IndexWriter tryDeleteDocument (dropDeletedSegment, change accounting) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call tryDeleteDocument on the only live document of a segment, and closing that segment's pooled reader inside IndexWriter throws an IOException; the writer stays open and you keep using it. You expect what the writer serves to its NRT readers to be what commit persists, and hasUncommittedChanges to report the pending change. Instead the segment is already gone from the writer's SegmentInfos but checkpoint and changed were skipped, so new NRT readers no longer show the document while hasUncommittedChanges is false, commit returns -1 and, after close, the committed index still contains the document.

## Expected vs actual

- Expected: You expect what the writer serves to its NRT readers to be what commit persists, and hasUncommittedChanges to report the pending change.
- Actual: The segment is already gone from the writer's SegmentInfos but checkpoint and changed were skipped, so new NRT readers no longer show the document while hasUncommittedChanges is false, commit returns -1 and, after close, the committed index still contains the document.

## Files

- [src/org/apache/lucene/index/TestTryDeleteDropSegmentFailure.java](src/org/apache/lucene/index/TestTryDeleteDropSegmentFailure.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestTryDeleteDropSegmentFailure
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
