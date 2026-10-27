# A commit that fails before startCommit (merge-on-commit setup, doAfterFlush, the merge scheduler or the event listener) keeps references on all of its files, so the still-open writer never deletes them, not even on rollback or close

Bug LFV-456 · Area: index / IndexWriter commit failure path (prepareCommit, IndexFileDeleter reference counts, merge-on-commit) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call commit and it throws a non-tragic exception early, for example an IOException while Lucene opens the small segments it merges as part of the commit (on by default), or from your MergePolicy.findFullFlushMerges or doAfterFlush override; the writer stays open, as Lucene intends. You expect the writer to delete the files of that failed commit once they are no longer needed: after later merges and commits, on deleteUnusedFiles, on rollback (whose javadoc says it removes any temporary files) or on close. Instead prepareCommit has already taken a reference on every file of the commit and releases it only if startCommit fails, so these files keep a positive reference count and stay on disk until a new IndexWriter opens the directory.

## Expected vs actual

- Expected: You expect the writer to delete the files of that failed commit once they are no longer needed: after later merges and commits, on deleteUnusedFiles, on rollback (whose javadoc says it removes any temporary files) or on close.
- Actual: prepareCommit has already taken a reference on every file of the commit and releases it only if startCommit fails, so these files keep a positive reference count and stay on disk until a new IndexWriter opens the directory.

## Files

- [src/org/apache/lucene/index/TestFailedCommitLeaksFileRefs.java](src/org/apache/lucene/index/TestFailedCommitLeaksFileRefs.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestFailedCommitLeaksFileRefs
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
