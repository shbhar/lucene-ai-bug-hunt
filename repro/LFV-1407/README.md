# IndexWriter.forceMerge(n) can return normally although the merge it registered failed, when the failure happens in IndexWriter.mergeInit (for example a full disk while the merge writes pending doc-values updates): mergeInit's catch calls mergeFinish, which wakes forceMerge, before IndexWriter.merge records the failure (4.1.0 to main)

Finding LFV-1407 · Area: index / IndexWriter forced merge (mergeInit failure path, mergeFinish, forceMerge wait loop, tragic events) · Priority: low · Impact: wrong error · Novelty: new

Finding page: [../../bugs/LFV-1407.html](../../bugs/LFV-1407.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call forceMerge(1) and the merge it starts fails early, inside IndexWriter.mergeInit, for example because the disk is full while the merge writes doc-values updates that another thread made during the force merge. You expect forceMerge to throw, as it does when the same failure happens one step later inside the merge itself. Instead it can return normally: mergeInit's error path calls mergeFinish, which wakes the waiting forceMerge before IndexWriter.merge has recorded the failure, and only afterwards does the failure close the writer and roll back its uncommitted changes.

## Expected vs actual

- Expected: `forceMerge(1)` throws (`IOException` "background merge hit exception" or `IllegalStateException`), as for a failure in `mergeMiddle`.
- Actual: `forceMerge(1)` returns normally; the writer is then closed by `IOException: simulated disk full`.

## Files

- [src/org/apache/lucene/index/TestForceMergeMergeInitFailure.java](src/org/apache/lucene/index/TestForceMergeMergeInitFailure.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
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
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestForceMergeMergeInitFailure
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestForceMergeMergeInitFailure.java](TestForceMergeMergeInitFailure.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
