# LatLon distance sort from a pole origin: the competitive bounding box (7 cm margin) rejects documents that tie with the queue bottom near the opposite pole, so the secondary SortField is skipped and top-N and searchAfter results are wrong

Bug LFV-550 · Area: core document / LatLon distance sort (LatLonPointDistanceComparator competitive bounding box, Rectangle.fromPointDistance pad) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index two points about 45 cm from the north pole with LatLonDocValuesField and sort with Sort(LatLonDocValuesField.newDistanceSort("p", -90, 146.5868...), new SortField("tie", INT)); both documents report the same distance (2.0015113815150492E7 m) and search(n=2) correctly returns [1, 0], the tie broken by "tie". You expect search(n=1) to return that head, doc 1, and one-hit searchAfter pages to return [1, 0]. Instead search(n=1) returns doc 0 and the pages return only doc 0: once doc 0 is the queue bottom, the comparator's pruning box (radius = the bottom's sloppy distance + 7 cm) leaves out doc 1, whose exact distance near the antipode exceeds that sloppy distance by more than 7 cm, so compareBottom returns -1 instead of 0 without computing doc 1's equal key and the secondary sort never runs.

## Expected vs actual

- Expected: You expect search(n=1) to return that head, doc 1, and one-hit searchAfter pages to return [1, 0].
- Actual: search(n=1) returns doc 0 and the pages return only doc 0: once doc 0 is the queue bottom, the comparator's pruning box (radius = the bottom's sloppy distance + 7 cm) leaves out doc 1, whose exact distance near the antipode exceeds that sloppy distance by more than 7 cm, so compareBottom returns -1 instead of 0 without computing doc 1's equal key and the secondary sort never runs.

## Files

- [src/org/apache/lucene/document/TestLatLonDistanceSortPoleTie.java](src/org/apache/lucene/document/TestLatLonDistanceSortPoleTie.java) (JUnit test)

## Run it

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
     execute --select-class org.apache.lucene.document.TestLatLonDistanceSortPoleTie
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
