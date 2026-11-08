# After prepareCommit() and rollback(), an IndexWriter opened from that writer's NRT reader (setIndexCommit(reader.getIndexCommit())) passes the newest commit to IndexDeletionPolicy.onInit twice, so KeepLastNCommitsDeletionPolicy(2) deletes an older commit it should keep (since 5.3.0)

Finding LFV-1306 · Area: core / index: IndexFileDeleter initialisation (IndexDeletionPolicy.onInit when an IndexWriter is opened from an NRT reader's IndexCommit) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1306.html](../../bugs/LFV-1306.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

A writer commits twice (segments_1, segments_2), an NRT reader is opened on it, a further change is prepared with prepareCommit() and then abandoned with rollback(), and a new IndexWriter is opened with setIndexCommit(nrtReader.getIndexCommit()) and KeepLastNCommitsDeletionPolicy(2). You expect IndexDeletionPolicy.onInit to be shown the two commits in the directory, [1, 2], and both to stay. Instead onInit is shown [1, 2, 2]: IndexFileDeleter looks for the current commit by the writer's next-write generation (3) instead of the generation of the current segments file (2), finds no match, reads segments_2 a second time through its stale-directory-listing fallback, and the policy deletes segments_1, so DirectoryReader.listCommits returns only [2].

## Expected vs actual

- Expected: `DirectoryReader.listCommits` gives generations [1, 2]: the policy keeps both commits.
- Actual: `expected:<[1, 2]> but was:<[2]>`: `segments_1` was deleted when the new writer opened (assertions on and off).

## Files

- [src/org/apache/lucene/index/TestKeepLastNAfterPreparedRollback.java](src/org/apache/lucene/index/TestKeepLastNAfterPreparedRollback.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
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
     execute --select-class org.apache.lucene.index.TestKeepLastNAfterPreparedRollback
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestKeepLastNAfterPreparedRollback.java](TestKeepLastNAfterPreparedRollback.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
