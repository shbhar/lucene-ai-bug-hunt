# DirectoryReader.listCommits throws CorruptIndexException (CorruptSegmentInfoException on main) on a healthy index when the deletion policy deletes an older commit while listCommits is reading it (known: #8894 / LUCENE-7843) (released since 6.5.0)

Finding LFV-1095 · Area: index / DirectoryReader.listCommits (commit enumeration) · Priority: low · Impact: crash · Novelty: known upstream

AI-assisted finding, reproduced on Lucene main but not yet independently re-verified, and not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call DirectoryReader.listCommits while an older commit is being deleted (a snapshot is released and the application commits, or a concurrent commit under the default policy) and expect the existing commits, since its javadoc says a commit in progress may or may not be returned. Instead it throws CorruptIndexException: readCommit wraps the NoSuchFileException of the deleted commit's .si file into a corruption exception, which listCommits does not catch.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: It throws CorruptIndexException: readCommit wraps the NoSuchFileException of the deleted commit's .si file into a corruption exception, which listCommits does not catch.

## Files

- [src/org/apache/lucene/index/TestListCommitsOlderCommitDeleted.java](src/org/apache/lucene/index/TestListCommitsOlderCommitDeleted.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestListCommitsOlderCommitDeleted
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this finding's part of the hunt used: [../../models/findsegmentsfile-commit-discovery-tla/](../../models/findsegmentsfile-commit-discovery-tla/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
