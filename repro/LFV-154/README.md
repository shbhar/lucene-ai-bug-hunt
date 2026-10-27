# CachingCollector#replay (and CachingCollectorManager#replay) lets a CollectionTerminatedException from the second-pass collector escape to the caller instead of ending only that leaf as IndexSearcher does, so replaying into an early-terminating collector, such as a TopFieldCollector sorted by the index sort, throws

Bug LFV-154 · Area: search / CachingCollector replay (CachingCollector, CachingCollectorManager) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a query once through a CachingCollector (or CachingCollectorManager) and then replay the cached hits into a second-pass collector, expecting the same result as running that collector through IndexSearcher. When the second-pass collector stops a segment early by throwing CollectionTerminatedException, which is how LeafCollector#collect documents early termination and what a stock TopFieldCollector sorted by the index sort does once a segment has more than 1000 hits, IndexSearcher simply moves on to the next segment, but replay lets the exception escape to the caller, without a stack trace. Catching it around replay is no workaround: the later segments are never replayed, so in the repro the top 10 come back as [1000..1009] instead of [0..9].

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/search/TestCachingCollectorManagerReplayTermination.java](src/org/apache/lucene/search/TestCachingCollectorManagerReplayTermination.java) (JUnit test)
- [src/org/apache/lucene/search/TestCachingCollectorReplayTerminationLegacy.java](src/org/apache/lucene/search/TestCachingCollectorReplayTerminationLegacy.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestCachingCollectorManagerReplayTermination

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestCachingCollectorReplayTerminationLegacy
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
