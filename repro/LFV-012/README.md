# An orphaned NRT reader can report isCurrent true for different index content after a new writer session

Bug LFV-012 · Area: index / IndexWriter NRT · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-012.html](../../bugs/LFV-012.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You keep an NRT reader after its writer is rolled back or closed, then open a new writer on the same directory and change the index. You expect reader.isCurrent to return false. Instead SegmentInfos.version restarts in each writer session and the closed-writer branch of isCurrent compares only versions, so the stale reader reports true for different content.

The test shows that an NRT reader whose writer was rolled back reports `isCurrent() == true` after a new writer on the same directory has committed different content, because the new session's second commit lands on the reader's SegmentInfos version (10) and the closed-writer branch of `isCurrent` compares only versions.

## Expected vs actual

Expected `isCurrent()` to be false (reader maxDoc 4, latest commit maxDoc 3); actual true, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/index/TestOrphanedNrtReaderIsCurrent.java](src/org/apache/lucene/index/TestOrphanedNrtReaderIsCurrent.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestOrphanedNrtReaderIsCurrent
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
