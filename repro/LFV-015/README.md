# After rolled-back segment names are reused, reopening an old NRT reader throws a misleading IllegalStateException

Bug LFV-015 · Area: index / IndexWriter NRT · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open an NRT reader from a writer that is then rolled back without committing, a later writer reuses the same segment names and commits, and you call openIfChanged(oldReader, commit). You expect a new reader or an AlreadyClosedException, as documented. Instead the LUCENE-5931 guard throws an IllegalStateException that blames the application for removing index files, although Lucene's own IndexFileDeleter deleted them, and its OpenMode.CREATE advice also fails.

The test shows that an NRT reader opened from a never-committed writer, kept across that writer's rollback and a second writer's rollback, cannot be reopened on a third writer's commit that reuses the segment name `_0`.

## Expected vs actual

Expected `DirectoryReader.openIfChanged(r, commit)` to return a new reader on the 2-document commit (its javadoc). Actual: `IllegalStateException: same segment _0 has invalid doc count change; likely you are re-opening a reader after illegally removing index files yourself...`, though Lucene's own IndexFileDeleter removed them.

## Files

- [src/org/apache/lucene/index/TestReopenAfterTwoRollbacks.java](src/org/apache/lucene/index/TestReopenAfterTwoRollbacks.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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
     execute --select-class org.apache.lucene.index.TestReopenAfterTwoRollbacks
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
