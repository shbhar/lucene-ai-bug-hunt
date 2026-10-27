# A reopened reader returns the doc values of a discarded writer session or commit when a later writer session reuses the same doc-values generation number

Bug LFV-109 · Area: index / DirectoryReader reopen with doc-values updates (generation numbers reused across writer sessions) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You keep a reader open while a writer session updates doc values and then ends without committing (rollback, or close with commitOnClose=false), or while a writer opened on an older commit with setIndexCommit discards the newer commit; a later writer session updates doc values of the same segment and commits, and you reopen the old reader with DirectoryReader.openIfChanged. You expect the reopened reader to show the latest commit, as a fresh DirectoryReader.open on that commit does ('If the IndexCommit differs from what the provided reader is searching, open and return a new reader'). Instead it silently returns the discarded values (for example 99 11 instead of 10 300), because the later session wrote different files under the same doc-values generation number and the reopen reuses the old reader's doc-values producer for that number.

## Expected vs actual

- Expected: You expect the reopened reader to show the latest commit, as a fresh DirectoryReader.open on that commit does ('If the IndexCommit differs from what the provided reader is searching, open and return a new reader').
- Actual: It silently returns the discarded values (for example 99 11 instead of 10 300), because the later session wrote different files under the same doc-values generation number and the reopen reuses the old reader's doc-values producer for that number.

## Files

- [src/org/apache/lucene/index/TestReopenAfterRollbackDocValuesGenReuse.java](src/org/apache/lucene/index/TestReopenAfterRollbackDocValuesGenReuse.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestReopenAfterRollbackDocValuesGenReuse
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
