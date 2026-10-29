# Data race on SegmentInfos.version between newSegmentName and checkpoint

Bug LFV-017 · Area: index / IndexWriter NRT · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

Several indexing threads allocate new segment names while the writer checkpoints. You expect every SegmentInfos.version increment to be kept, so the version check of a reader is reliable. Instead newSegmentName increments the version under synchronized(segmentInfos) while other increments hold the IndexWriter monitor, so an increment can be lost or go backwards and a stale reader can look current.

## Expected vs actual

- Expected: You expect every SegmentInfos.version increment to be kept, so the version check of a reader is reliable.
- Actual: newSegmentName increments the version under synchronized(segmentInfos) while other increments hold the IndexWriter monitor, so an increment can be lost or go backwards and a stale reader can look current.

## Files

- [src/R6Hook.java](src/R6Hook.java)
- [src/R6Count.java](src/R6Count.java)
- [src/TestR6SegmentInfosVersionRace.java](src/TestR6SegmentInfosVersionRace.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main b83101cdd2 (2026-10-23): reproduced.
- Recorded outcome of run 1 on Lucene main a9779ef3c193: 2 test(s) run, 2 failed (testAddIndexesRaceLowersVersionAndHidesChanges, testDwptCreationRaceLosesIncrements).
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.
- Not runnable from this site as is: this repro also needs extra JVM setup (such as an instrumentation hook) that is not exported here; see the comments at the top of the source.
- This repro reads private Lucene state by reflection (setAccessible), so it depends on internals that may change.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx4g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestR6SegmentInfosVersionRace
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
