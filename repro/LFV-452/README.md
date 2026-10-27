# If closing a pooled segment reader throws inside IndexWriter.deleteAll, the segments are already cleared but the change is never counted: the writer stays open and its NRT readers show an empty index, yet hasUncommittedChanges is false and commit and close keep every document, until a later unrelated commit silently applies the failed deleteAll

Bug LFV-452 · Area: index / IndexWriter deleteAll (change accounting on the exception path, ReaderPool.dropAll) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You commit two documents, open and close an NRT reader (so the writer keeps pooled segment readers), and call deleteAll; closing one of the pooled readers throws (for example a core ClosedListener your cache registered throws its declared IOException, or closing a file fails), so deleteAll throws an IOException and the writer stays open. You expect the writer to stay self-consistent: what its NRT readers show is what commit persists, and hasUncommittedChanges reports the pending change. Instead deleteAll has already removed every segment but skipped counting the change, so new NRT readers show 0 documents while hasUncommittedChanges is false, commit returns -1 and writes nothing, close keeps both documents on disk, and the next unrelated addDocument plus commit silently applies the deleteAll that failed.

## Expected vs actual

- Expected: You expect the writer to stay self-consistent: what its NRT readers show is what commit persists, and hasUncommittedChanges reports the pending change.
- Actual: deleteAll has already removed every segment but skipped counting the change, so new NRT readers show 0 documents while hasUncommittedChanges is false, commit returns -1 and writes nothing, close keeps both documents on disk, and the next unrelated addDocument plus commit silently applies the deleteAll that failed.

## Files

- [src/org/apache/lucene/index/TestFailedDeleteAllHalfApplied.java](src/org/apache/lucene/index/TestFailedDeleteAllHalfApplied.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestFailedDeleteAllHalfApplied
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
