# PrimaryNode.releaseCopyState leaks the pending-copy count when IndexWriter.decRefDeleter throws (for example after an I/O error closed the IndexWriter while a replica was copying), so PrimaryNode.close then waits forever under the default remote-close timeout

Bug LFV-1025 · Area: replicator / NRT segment replication (PrimaryNode.releaseCopyState, PrimaryNode.close) · Priority: unranked · Impact: hang · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

A replica takes primary.getCopyState, then an I/O error while indexing (say, disk full while creating a stored-fields file) closes the primary's IndexWriter, the replica calls releaseCopyState as documented ('Called once replica is done (or failed) copying an NRT point'), and the application calls primary.close; you expect close to return (or throw), because no copy is in progress any more. Instead close never returns: releaseCopyState first calls writer.decRefDeleter, which throws AlreadyClosedException, so the copyingCount.decrementAndGet after it never runs, and close loops in waitForAllRemotesToClose forever because the default remote-close timeout is -1 ('means forever'). Moving the decrement into a finally block makes close return at once.

## Expected vs actual

- Expected: You expect close to return (or throw), because no copy is in progress any more.
- Actual: close never returns: releaseCopyState first calls writer.decRefDeleter, which throws AlreadyClosedException, so the copyingCount.decrementAndGet after it never runs, and close loops in waitForAllRemotesToClose forever because the default remote-close timeout is -1 ('means forever').

## Files

- [src/org/apache/lucene/replicator/nrt/TestPrimaryNodeCloseAfterTragedy.java](src/org/apache/lucene/replicator/nrt/TestPrimaryNodeCloseAfterTragedy.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.replicator.nrt.TestPrimaryNodeCloseAfterTragedy
```

The module(s) facet, replicator also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
