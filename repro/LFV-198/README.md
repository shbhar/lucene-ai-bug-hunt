# MultiCollectorManager wrapping exactly one manager whose collectors are MultiCollectors (for example a nested MultiCollectorManager) hands that manager the wrong collectors in reduce, so its second and later sub-managers return silently wrong results or throw ClassCastException

Bug LFV-198 · Area: search / MultiCollectorManager (composition of CollectorManagers) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap one collector manager in a MultiCollectorManager, for example new MultiCollectorManager(new MultiCollectorManager(top1, top5)) from code that builds the manager list dynamically, and expect the same results as searching with the inner manager directly. Instead, because MultiCollector.wrap returns a single collector unwrapped, the outer manager's reduce takes the inner manager's own MultiCollector for its wrapper and unwraps it, so the inner manager's second child is reduced over the first child's collectors. In the repro the top-5 list comes back with 0 hits instead of 5, and with a top-k child next to a hit-count child the count manager's reduce throws ClassCastException (TopScoreDocCollector cannot be cast to TotalHitCountCollector).

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Because MultiCollector.wrap returns a single collector unwrapped, the outer manager's reduce takes the inner manager's own MultiCollector for its wrapper and unwraps it, so the inner manager's second child is reduced over the first child's collectors.

## Files

- [src/org/apache/lucene/search/TestMultiCollectorManagerSingleNested.java](src/org/apache/lucene/search/TestMultiCollectorManagerSingleNested.java) (JUnit test)
- [src/org/apache/lucene/search/TestMultiCollectorManagerSingleNestedLegacy.java](src/org/apache/lucene/search/TestMultiCollectorManagerSingleNestedLegacy.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestMultiCollectorManagerSingleNested

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestMultiCollectorManagerSingleNestedLegacy
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
