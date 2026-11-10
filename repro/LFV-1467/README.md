# Sandbox HistogramCollectorManager never clears its bulk-collected leaf map, so a second search with the same manager on the same reader silently returns an empty histogram for every segment on the point-tree path (since 10.3.0)

Finding LFV-1467 · Area: sandbox / facets: plain histograms (HistogramCollectorManager's per-manager leafBulkCollected map, used by HistogramCollector's point-tree bulk path) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1467.html](../../bugs/LFV-1467.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build one HistogramCollectorManager("f", 5) over a LongField and call IndexSearcher.search(MatchAllDocsQuery.INSTANCE, manager) twice on the same reader. You expect the same histogram twice, as in 10.2.x, where the manager held no state: its javadoc does not say it serves one search (TopScoreDocCollectorManager and TopFieldCollectorManager do), and core's TotalHitCountCollectorManager clears its own per-leaf map in reduce() so that one instance can be reused. Instead the second search returns an empty histogram, because the leafBulkCollected map that since 10.3.0 stops the intra-segment partitions of one search from counting a segment twice is created once per manager (HistogramCollectorManager.java:83), shared by every collector of every search (:88) and never cleared (reduce, :91-101), so HistogramCollector skips every segment the map already holds and ends collection on it (HistogramCollector.java:92-98).

## Expected vs actual

- Expected: The second search returns `[0=>1]` again, so `get(0)` is 1, as in the first search.
- Actual: The second search returns `[]`: `AssertionError: expected:<1> but was:<0>` at line 44, with assertions on and off.

## Files

- [src/org/apache/lucene/sandbox/facet/plain/histograms/TestHistogramCollectorManagerReuse.java](src/org/apache/lucene/sandbox/facet/plain/histograms/TestHistogramCollectorManagerReuse.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.sandbox.facet.plain.histograms.TestHistogramCollectorManagerReuse
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestHistogramCollectorManagerReuse.java](TestHistogramCollectorManagerReuse.java)

Formal models and scripts this finding's part of the hunt used: [../../models/sandbox-histogramcollectormanager-leafbulkcollected-map-never-cleared-reused-manager-on-same-reader-returns-empty-histogram-match-all-and-point-range-since-10-3-0-verify-collector-manager-reuse-siblings/](../../models/sandbox-histogramcollectormanager-leafbulkcollected-map-never-cleared-reused-manager-on-same-reader-returns-empty-histogram-match-all-and-point-range-since-10-3-0-verify-collector-manager-reuse-siblings/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
