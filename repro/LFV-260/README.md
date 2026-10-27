# An NRT replica's commit can store in VERSION_KEY the version of a newer NRT point installed during its fsync, so the commit claims a version ahead of the SegmentInfos it holds

Bug LFV-260 · Area: replicator / NRT segment replication (ReplicaNode.commit commit user data, PrimaryNode.getLastCommitVersion) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run Lucene's NRT segment replication and call commit on a replica while the primary keeps publishing NRT points, and during the commit's fsync the replica installs a newer point (version 5, documents [d1, d2]). You expect the commit's VERSION_KEY to name the version whose content it holds (version 3, [d1]), or at worst an older one, because the module uses that value as the place to resume replaying operations and PrimaryNode.commit accepts only a lagging value ('replaying more ops than necessary'). Instead commit writes the SegmentInfos it captured before the fsync (version 3) but reads VERSION_KEY from the searcher that is current after it (5), so a primary started on that commit reports getLastCommitVersion=5 while it holds only d1, and a replay from version 5 skips d2.

## Expected vs actual

- Expected: You expect the commit's VERSION_KEY to name the version whose content it holds (version 3, [d1]), or at worst an older one, because the module uses that value as the place to resume replaying operations and PrimaryNode.commit accepts only a lagging value ('replaying more ops than necessary').
- Actual: commit writes the SegmentInfos it captured before the fsync (version 3) but reads VERSION_KEY from the searcher that is current after it (5), so a primary started on that commit reports getLastCommitVersion=5 while it holds only d1, and a replay from version 5 skips d2.

## Files

- [src/org/apache/lucene/replicator/nrt/TestReplicaCommitVersionKey.java](src/org/apache/lucene/replicator/nrt/TestReplicaCommitVersionKey.java) (JUnit test)
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
     execute --select-class org.apache.lucene.replicator.nrt.TestReplicaCommitVersionKey
```

The module(s) facet, replicator also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
