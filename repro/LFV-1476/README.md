# ReplicaNode.close() throws NullPointerException and never releases the index write lock when start() failed or was never called, so the index stays write-locked for the rest of the JVM (since 6.0.0)

Finding LFV-1476 · Area: replicator / NRT replication: ReplicaNode start() failure and close() cleanup (index write lock) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1476.html](../../bugs/LFV-1476.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You subclass ReplicaNode (as the module's SimpleReplicaNode does), call start() and, when it fails with an IOException (in the minimal repro sendNewReplica() throws its declared IOException; in an earlier test the replica Directory's first listAll() throws), close the node, then open a new IndexWriter on the same FSDirectory path. You expect close() (Node implements java.io.Closeable) to release the write lock that ReplicaNode's constructor took 'since we act like an IndexWriter', as IndexWriter releases its own lock when its constructor fails, so the path can be opened again. Instead close() throws NullPointerException ('Cannot invoke ReferenceManager.close() because this.mgr is null') at its first step, because the SearcherManager is created only near the end of start(), so it never reaches IOUtils.close(writeFileLock, dir), and once that exception is caught a new IndexWriter on the path fails with LockObtainFailedException 'Lock held by this virtual machine'; a node that is closed without ever calling start() hits the same NullPointerException and held lock.

## Expected vs actual

- Expected: `close()` returns, frees `write.lock`, and a new `IndexWriter` opens on the same path.
- Actual: `close()` throws `NullPointerException: ... because "this.mgr" is null` at `ReplicaNode.java:683`, and the test framework reports the leaked `write.lock` handle.

## Files

- [src/org/apache/lucene/replicator/nrt/TestReplicaNodeCloseAfterFailedStart.java](src/org/apache/lucene/replicator/nrt/TestReplicaNodeCloseAfterFailedStart.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.replicator.nrt.TestReplicaNodeCloseAfterFailedStart
```

The module(s) facet, replicator also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestReplicaNodeCloseAfterFailedStart.java](TestReplicaNodeCloseAfterFailedStart.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
