# IndexWriter.prepareCommit() re-reads the shared pendingSeqNo field after its post-commit maybeMerge, so if another thread finishes that commit and prepares the next one meanwhile, prepareCommit() returns the next commit's sequence number

Finding LFV-1127 · Area: index / IndexWriter two-phase commit (sequence numbers) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1127.html](../../bugs/LFV-1127.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

The main thread adds a document and thread A calls IndexWriter.prepareCommit(); while A is still in the merge step that prepareCommit() runs after publishing its pending commit, the main thread calls commit() (which finishes A's commit and returns 3), adds another document (sequence number 4) and calls prepareCommit() again. You expect A's prepareCommit() to return 3, as its javadoc promises ('All sequence numbers &lt;= this value will be reflected in the commit, and all others will not') and as commit() returned for the same commit. Instead it returns 6, the next commit's number, because prepareCommit() re-reads the shared pendingSeqNo field after its maybeMerge instead of returning its own result; the committed index itself is correct.

## Expected vs actual

- Expected: Thread A's `prepareCommit()` returns 3, what `commit()` returned for the same commit.
- Actual: It returns 6, the next commit's number: `AssertionError: expected:<3> but was:<6>`.

## Files

- [src/org/apache/lucene/index/TestPrepareCommitOwnSeqNo.java](src/org/apache/lucene/index/TestPrepareCommitOwnSeqNo.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestPrepareCommitOwnSeqNo
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestPrepareCommitOwnSeqNo.java](TestPrepareCommitOwnSeqNo.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
