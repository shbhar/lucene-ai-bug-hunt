# CachingCollectorManager (new in 10.6.0) does not document that it is single-use: reused for a second search, replay replays the hits of both searches and one earlier overflow keeps isCached false

Bug LFV-907 · Area: search / CachingCollectorManager lifecycle (documentation; new public API in 10.6.0) · Priority: low · Impact: minor · Novelty: new

Bug page: [../../bugs/LFV-907.html](../../bugs/LFV-907.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap a reusable CollectorManager in a CachingCollectorManager, run a search with it, then run a second search with the same instance and call isCached and replay(secondPassManager), the workflow the class javadoc shows for one search. Nothing in its javadoc says an instance serves only one search, unlike TopScoreDocCollectorManager and TopFieldCollectorManager, which say 'a new collectorManager should be created for each search due to its internal states', while Lucene's stated norm (TotalHitCountCollectorManager) is that managers are generally reusable. Instead the manager keeps the per-slice caches of every search in one list that is never cleared, so replay returns the hits of both searches (the repro: [0..9] instead of the second search's [3..9]) and one earlier overflow makes isCached false for every later search, with no error.

The test shows (LFV-907, documentation defect) that a `CachingCollectorManager` used for two searches replays the hits of both searches, because it never clears its per-slice cache list; its javadoc does not say an instance serves one search.

## Expected vs actual

Expected (after searching MatchAllDocsQuery twice over one document with a stateless delegate): `replay()` returns the last search's 1 hit; actual (-ea and -da): 2. The intended fix is a javadoc sentence, so this test documents the gap rather than a future green test.

## Files

- [src/org/apache/lucene/search/TestCachingCollectorManagerReuse.java](src/org/apache/lucene/search/TestCachingCollectorManagerReuse.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestCachingCollectorManagerReuse
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
