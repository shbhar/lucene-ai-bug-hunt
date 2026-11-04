# CopyOneFile.close() called after visit() has finished the copy throws IllegalStateException 'file &lt;name&gt; was not actually being copied?' instead of doing nothing, because visit() already closes the copy itself and close() keeps no closed state, although CopyOneFile is a public Closeable (NRT replicator, every release since 6.0.0)

Finding LFV-1366 · Area: replicator / NRT replication: CopyOneFile (the per-file copy helper behind every CopyJob; the 'copying' bookkeeping in ReplicaNode.finishCopyFile) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You copy one file to an NRT replica with CopyOneFile (public, implements Closeable), call visit() until it returns true (the whole file is copied and its checksum verified), and then close the CopyOneFile you created, for example with try-with-resources. You expect close() to do nothing, because Closeable.close() says that closing an already closed stream has no effect and neither CopyOneFile's nor visit()'s javadoc says otherwise. Instead close() throws IllegalStateException 'file &lt;name&gt; was not actually being copied?' from ReplicaNode.finishCopyFile: visit() already called close() itself (CopyOneFile.java:129), and close() (:87-91) keeps no closed state, so it unregisters the file name a second time.

## Expected vs actual

- Expected: `close()` after the completed `visit()` returns normally, and the test passes.
- Actual: `close()` throws `IllegalStateException: file f was not actually being copied?` at line 64, with assertions on and off.

## Files

- [src/org/apache/lucene/replicator/nrt/TestCopyOneFileCloseAfterVisit.java](src/org/apache/lucene/replicator/nrt/TestCopyOneFileCloseAfterVisit.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/replicator/build/libs/lucene-replicator-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.replicator.nrt.TestCopyOneFileCloseAfterVisit
```

The module(s) facet, replicator also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
