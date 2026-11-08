# While IndexWriter.deleteAll() runs, another thread can get hasUncommittedChanges() == false and an NRT reader's isCurrent() == true (openIfChanged() returns null) despite an acknowledged, uncommitted delete, because deleteAll discards the buffered deletes and doc-values updates before it counts its own change (since 4.3.1)

Finding LFV-1398 · Area: index / IndexWriter.deleteAll change accounting vs. concurrent status and NRT observers (DocumentsWriter.lockAndAbortAll, IndexWriter.hasUncommittedChanges / nrtIsCurrent, DirectoryReader.isCurrent / openIfChanged, ControlledRealTimeReopenThread) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1398.html](../../bugs/LFV-1398.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add and commit document 1, open an NRT reader with DirectoryReader.open(writer), call deleteDocuments(new Term("id", "1")) (hasUncommittedChanges() is now true and the reader's isCurrent() false), and start deleteAll() on another thread. You expect any thread that asks while deleteAll() runs to still hear 'there are uncommitted changes' and 'this reader is stale', because before deleteAll the delete is pending and after it deleteAll's own change is. Instead, for a stretch of every deleteAll() call, hasUncommittedChanges() returns false and the reader's isCurrent() returns true (openIfChanged() returns null): DocumentsWriter.lockAndAbortAll() has already thrown the buffered delete away, and deleteAll() counts its own change only at its very end.

## Expected vs actual

- Expected: While `deleteAll()` runs, `hasUncommittedChanges()` stays `true` and the NRT reader's `isCurrent()` stays `false`.
- Actual: `java.lang.AssertionError: hasUncommittedChanges()=false, isCurrent()=true`, with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestDeleteAllUncommittedChangesWindow.java](src/org/apache/lucene/index/TestDeleteAllUncommittedChangesWindow.java) (JUnit test)

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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestDeleteAllUncommittedChangesWindow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestDeleteAllUncommittedChangesWindow.java](TestDeleteAllUncommittedChangesWindow.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
