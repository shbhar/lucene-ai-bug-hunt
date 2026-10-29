# An NRT replica's merge pre-copy callback (ReplicaNode.launchPreCopyMerge) synchronizes on itself instead of the ReplicaNode, so a primary change that lands while it installs the merged files leaves the old primary's pre-copied merged files pending and on disk

Bug LFV-245 · Area: replicator / NRT segment replication (ReplicaNode.launchPreCopyMerge merge pre-copy, ReplicaNode.newNRTPoint) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run Lucene's NRT segment replication with merge pre-copy: the primary asks replica B to pre-copy a merged segment, and just as B has copied the bytes and is renaming the files into place, another node is promoted to primary and B receives its first NRT point (primaryGen 2). You expect B either to install the merged files and then discard them in the switch, or to notice the switch and cancel the pre-copy, because newNRTPoint says it discards the old primary's pre-copied merged segments and the callback itself checks whether the primary changed. Instead the callback's synchronized (this) locks the anonymous CopyJob.OnceDone, not the ReplicaNode, so the switch runs in the middle of it and B follows primaryGen 2 with the old primary's merged files [_2.cfe, _2.cfs, _2.si] still in pendingMergeFiles and on disk (with -ea, the new primary's later pre-copy of a same-named merged segment then fails with 'file "_2.si" is already being warmed!').

The test shows that `ReplicaNode.launchPreCopyMerge`'s OnceDone locks itself, not the node, so a `newNRTPoint` with a new primaryGen can run while it installs pre-copied merged files, and the old primary's files are registered afterwards.

## Expected vs actual

Expected: after the switch to primaryGen 2 the old primary's pre-copied files are discarded (`pendingMergeFiles` empty); actual: `pendingMergeFiles at primaryGen 2 expected:<[]> but was:<[_0.si]>`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/replicator/nrt/TestMergePreCopyPrimaryChange.java](src/org/apache/lucene/replicator/nrt/TestMergePreCopyPrimaryChange.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/replicator/build/libs/lucene-replicator-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.replicator.nrt.TestMergePreCopyPrimaryChange
```

The module(s) facet, replicator also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
