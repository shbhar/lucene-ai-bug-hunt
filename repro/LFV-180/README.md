# CachingCollector (and CachingCollectorManager) never calls LeafCollector#finish on the collector it wraps, nor on the second-pass collector when it replays cached scores, so finish-dependent collectors such as FacetsCollector silently report no hits

Bug LFV-180 · Area: search / CachingCollector first pass and score-caching replay (CachingCollector, CachingCollectorManager) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap a FacetsCollector, or any collector that does its per-segment work in LeafCollector#finish, in a CachingCollector or CachingCollectorManager so you can run the query once and replay the hits later, or you replay a score-caching cache into such a collector, and you expect the same facet counts as a plain IndexSearcher search. Instead the collector reports no hits: CachingCollector's per-leaf wrapper overrides finish for its own bookkeeping and never passes the call on, and the score-caching replay never calls finish at all, although the no-score replay does. In the repro a query that matches 20 documents gives 0 matching docs, with no error unless assertions are enabled.

## Expected vs actual

- Expected: `[20, 20]`: the wrapped `FacetsCollector` and the replay target each report the 20 hits.
- Actual: `[0, 0]`: both report no `MatchingDocs`, with no error, with assertions on and off.

## Files

- [src/org/apache/lucene/facet/TestCachingCollectorFinish.java](src/org/apache/lucene/facet/TestCachingCollectorFinish.java) (JUnit test)
- [src/org/apache/lucene/facet/TestCachingCollectorFinishLegacy.java](src/org/apache/lucene/facet/TestCachingCollectorFinishLegacy.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.TestCachingCollectorFinish

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.TestCachingCollectorFinishLegacy
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
