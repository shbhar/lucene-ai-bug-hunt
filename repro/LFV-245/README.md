# An NRT replica's merge pre-copy callback (ReplicaNode.launchPreCopyMerge) synchronizes on itself instead of the ReplicaNode, so a primary change that lands while it installs the merged files leaves the old primary's pre-copied merged files pending and on disk

Bug LFV-245 · Area: replicator / NRT segment replication (ReplicaNode.launchPreCopyMerge merge pre-copy, ReplicaNode.newNRTPoint) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run Lucene's NRT segment replication with merge pre-copy: the primary asks replica B to pre-copy a merged segment, and just as B has copied the bytes and is renaming the files into place, another node is promoted to primary and B receives its first NRT point (primaryGen 2). You expect B either to install the merged files and then discard them in the switch, or to notice the switch and cancel the pre-copy, because newNRTPoint says it discards the old primary's pre-copied merged segments and the callback itself checks whether the primary changed. Instead the callback's synchronized (this) locks the anonymous CopyJob.OnceDone, not the ReplicaNode, so the switch runs in the middle of it and B follows primaryGen 2 with the old primary's merged files [_2.cfe, _2.cfs, _2.si] still in pendingMergeFiles and on disk (with -ea, the new primary's later pre-copy of a same-named merged segment then fails with 'file "_2.si" is already being warmed!').

## Expected vs actual

- Expected: You expect B either to install the merged files and then discard them in the switch, or to notice the switch and cancel the pre-copy, because newNRTPoint says it discards the old primary's pre-copied merged segments and the callback itself checks whether the primary changed.
- Actual: The callback's synchronized (this) locks the anonymous CopyJob.OnceDone, not the ReplicaNode, so the switch runs in the middle of it and B follows primaryGen 2 with the old primary's merged files [_2.cfe, _2.cfs, _2.si] still in pendingMergeFiles and on disk (with -ea, the new primary's later pre-copy of a same-named merged segment then fails with 'file "_2.si" is already being warmed!').

## Files

- [src/org/apache/lucene/replicator/nrt/TestMergePreCopyOnceDoneMonitor.java](src/org/apache/lucene/replicator/nrt/TestMergePreCopyOnceDoneMonitor.java) (JUnit test)
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
     execute --select-class org.apache.lucene.replicator.nrt.TestMergePreCopyOnceDoneMonitor
```

The module(s) facet, replicator also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
