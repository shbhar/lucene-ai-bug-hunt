# After a primary change, an NRT replica keeps a file it already copied from the old primary when the new primary's file has the same name, matching by name only, so the replica serves and commits the wrong documents with CheckIndex clean

Bug LFV-241 · Area: replicator / NRT segment replication (CopyJob.transferAndCancel, ReplicaNode.newNRTPoint) · Priority: medium · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-241.html](../../bugs/LFV-241.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run Lucene's NRT segment replication with failover: replica A is promoted to primary (a higher primaryGen) while replica B is still copying an NRT point from the old primary and has already finished one of its files, for example _1_1.liv. You expect B, once it has installed the new primary's NRT point, to search exactly the new primary's documents, because the module discards pre-copied data on a primary change and treats two files as the same only if name, header and footer match. Instead CopyJob._transferAndCancel keeps the old primary's finished temp file because the new primary's file has the same name, so B, at the new primary's version, shows [a0, b1] instead of [a0, a1], commits that state, and CheckIndex reports the index clean.

## Expected vs actual

- Expected: Replica B at P2's version 9 searches and commits P2's documents, [a0, a1].
- Actual: B at version 9 searches and commits [a0, b1] (P1's `_1_1.liv` carried over), and `CheckIndex` is clean.

## Files

- [src/org/apache/lucene/replicator/nrt/TestNRTCopyAcrossPrimaryChange.java](src/org/apache/lucene/replicator/nrt/TestNRTCopyAcrossPrimaryChange.java) (JUnit test)
- [src/org/apache/lucene/replicator/nrt/InProcNodes.java](src/org/apache/lucene/replicator/nrt/InProcNodes.java)
- [src2/org/apache/lucene/replicator/nrt/InProcNodes.java](src2/org/apache/lucene/replicator/nrt/InProcNodes.java)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/replicator/build/libs/lucene-replicator-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.replicator.nrt.TestNRTCopyAcrossPrimaryChange
```

The module(s) facet, replicator also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestReplicaCarryOverAfterFailover.java](TestReplicaCarryOverAfterFailover.java)
- [fix-with-test.diff](fix-with-test.diff)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
