# NRT PrimaryNode: one I/O error during flushAndRefresh leaves the new point unpublished to replicas, and the retry reports no changes

Bug LFV-1024 · Area: replicator / NRT segment replication (PrimaryNode.flushAndRefresh, setCurrentInfos: NRT point publication) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run Lucene's NRT segment replication, index a document and call PrimaryNode.flushAndRefresh; it throws because opening one of the new files hits a transient I/O error, and once the error is gone you call flushAndRefresh again. You expect the retry to publish the new point to replicas, or the failed call to have left the previous point fully in place. Instead the failed call had already switched the primary's current point and released the previous point's files, but not the copy state that replicas are given, so the retry returns false ('no changes'), getCopyStateVersion stays behind getCurrentSearchingVersion, and replicas are not offered the new point until the index changes again.

## Expected vs actual

- Expected: You expect the retry to publish the new point to replicas, or the failed call to have left the previous point fully in place.
- Actual: The failed call had already switched the primary's current point and released the previous point's files, but not the copy state that replicas are given, so the retry returns false ('no changes'), getCopyStateVersion stays behind getCurrentSearchingVersion, and replicas are not offered the new point until the index changes again.

## Files

- [src/org/apache/lucene/replicator/nrt/TestPrimaryRetryAfterFailedPublish.java](src/org/apache/lucene/replicator/nrt/TestPrimaryRetryAfterFailedPublish.java) (JUnit test)

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
     execute --select-class org.apache.lucene.replicator.nrt.TestPrimaryRetryAfterFailedPublish
```

The module(s) facet, replicator also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
