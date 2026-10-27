# An NRT replica acts on a late notification from an older primary generation: ReplicaNode.maybeNewPrimary deletes the current primary's pre-copied merged segment before its only check, an assert, so the replica's cut-over to the merged point fails or the segment is copied again (with or without assertions), and with assertions off lastPrimaryGen also moves back

Bug LFV-270 · Area: replicator / NRT segment replication (ReplicaNode.newNRTPoint, maybeNewPrimary, launchPreCopyMerge: primaryGen fencing) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run Lucene's NRT segment replication and fail over cleanly: the old primary (generation 1) is closed, then replica A is promoted to primary (generation 2), and replica B follows A and pre-copies A's merged segment. A last notification from the old primary, delayed in transit, then reaches B; you expect B to ignore it, as it already ignores out-of-order older NRT versions, because the primary generation only ever grows. Instead ReplicaNode.maybeNewPrimary treats the older generation as a primary change and deletes A's pre-copied merged files before its only check (an assert), so B's cut-over to A's merged point fails with '_3.si cannot be read - it may be missing or corrupt' or B copies the merged segment again, and with assertions off B also sets lastPrimaryGen back to 1.

## Expected vs actual

- Expected: You expect B to ignore it, as it already ignores out-of-order older NRT versions, because the primary generation only ever grows.
- Actual: ReplicaNode.maybeNewPrimary treats the older generation as a primary change and deletes A's pre-copied merged files before its only check (an assert), so B's cut-over to A's merged point fails with '_3.si cannot be read - it may be missing or corrupt' or B copies the merged segment again, and with assertions off B also sets lastPrimaryGen back to 1.

## Files

- [src/org/apache/lucene/replicator/nrt/TestLateNotificationDropsPreCopiedMerge.java](src/org/apache/lucene/replicator/nrt/TestLateNotificationDropsPreCopiedMerge.java) (JUnit test)
- [src/org/apache/lucene/replicator/nrt/InProcNodes.java](src/org/apache/lucene/replicator/nrt/InProcNodes.java)

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

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed A0D1745
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=A0D1745 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.replicator.nrt.TestLateNotificationDropsPreCopiedMerge
```

The module(s) facet, replicator also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
