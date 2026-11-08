# XY distance sort used as a later Sort key drops closer tied hits, because XYPointDistanceComparator.setBottom keeps an old, too-small bounding box when the bottom grows (a missing-value bottom of +Infinity, or growth after 1024 bottom updates)

Finding LFV-1132 · Area: core / document: XY distance sort (XYDocValuesField.newDistanceSort, XYPointDistanceComparator) · Priority: medium · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1132.html](../../bugs/LFV-1132.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You sort three documents, (cat b, distance 1), (cat a, no location) and (cat a, distance 3), with Sort(new SortField("cat", STRING), XYDocValuesField.newDistanceSort("loc", 0, 0)) and ask for the top 1; you expect the (a, 3) document, because ties on cat are broken by ascending distance and a missing location counts as +Infinity and sorts last. Instead you get the (a, no location) document: when it becomes the queue bottom, setBottom skips rebuilding the comparator's bounding box because +Infinity is not below Float.MAX_VALUE, so the box built for the earlier bottom at distance 1 stays, and compareBottom rejects the (a, 3) document as outside it. The same stale box arises after 1024 bottom updates, when only every 64th update rebuilds it and the bottom grows on another one; a nearest-first distance sort is not affected because its bottom only shrinks.

## Expected vs actual

- Expected: `2`: doc 2 (category a, distance 3) sorts before doc 1 (category a, no location).
- Actual: `expected:<2> but was:<1>`: the document without a location is returned, with no error, with assertions on and off.

## Files

- [src/org/apache/lucene/document/TestXYDistanceSortAsSecondaryKey.java](src/org/apache/lucene/document/TestXYDistanceSortAsSecondaryKey.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces (the repro adapted to an API change on this commit).
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
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestXYDistanceSortAsSecondaryKey
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestXYDistanceSortAsSecondaryKey.java](TestXYDistanceSortAsSecondaryKey.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
