# LatLon distance sort used as a later Sort key drops closer tied hits after 1024 bottom updates, because LatLonPointDistanceComparator keeps a stale bounding box when the bottom grows (every release since 6.1.0)

Finding LFV-1136 · Area: core document / LatLon distance sort (LatLonPointDistanceComparator.setBottom: sampled competitive bounding box) · Priority: medium · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1136.html](../../bugs/LFV-1136.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search with Sort(cat INT, LatLonDocValuesField.newDistanceSort("loc", 0, 0)), so distance only breaks ties of cat, over 1026 documents: docs 0..1023 each have a smaller cat than the one before (so each becomes the new top hit) and lie about 1 km from the origin, and docs 1024 and 1025 share the smallest cat, with doc 1025 (about 1500 km away) closer than doc 1024 (about 2000 km). You expect doc 1025 as the top hit, because Sort says a later SortField breaks ties and the distance sort orders by ascending distance. You get doc 1024, with no error: after 1024 bottom updates the comparator rebuilds its competitive bounding box only on every 64th update, so it keeps the 1 km box built for the old bottom, and compareBottom rejects doc 1025 as outside that box without computing its distance.

## Expected vs actual

- Expected: Doc 1025: it ties doc 1024 on `cat` = 1000 and is closer (1,501 km against 2,002 km).
- Actual: Doc 1024 (`expected:<1025> but was:<1024>`), with no exception, with assertions on and off.

## Files

- [src/org/apache/lucene/document/TestLatLonDistanceSortSecondaryKey.java](src/org/apache/lucene/document/TestLatLonDistanceSortSecondaryKey.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestLatLonDistanceSortSecondaryKey
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestLatLonDistanceSortSecondaryKey.java](TestLatLonDistanceSortSecondaryKey.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
