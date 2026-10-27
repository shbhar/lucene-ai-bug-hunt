# Sequential DrillSideways skips the two-phase matches check of the second-cheapest drill-down dimension on segments without deletions, so documents that fail that dimension but are in its approximation are returned as drill-down hits and counted in the facets (released since 5.3.0)

Bug LFV-358 · Area: facet / DrillSideways (sequential DrillSidewaysScorer, drill-down-advance strategy, two-phase drill-down dimensions) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-358.html](../../bugs/LFV-358.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run DrillSideways without an executor on a DrillDownQuery with two or more dimensions, one of which is a two-phase query such as a distance or range drill-down with a fast-match query or a PhraseQuery, and expect the hits to match every dimension and each dimension's sideways counts to cover the documents that match all the other dimensions. Instead, on a segment with no deleted documents, when that dimension is the second-cheapest by approximation cost and the base query is broad, DrillSidewaysScorer never calls its TwoPhaseIterator#matches, because the condition 'acceptDocs == null || acceptDocs.get(docID) &amp;&amp; (dc.twoPhase == null || dc.twoPhase.matches)' parses as 'acceptDocs == null || (get &amp;&amp; matches)'. So every document in that dimension's approximation (inside the bounding box but outside the radius, the phrase words in another order) is treated as matching it, returned as a hit and counted in the facets, while concurrent DrillSideways and plain boolean queries on the same searcher give the correct sets.

## Expected vs actual

- Expected: 1 hit (doc 0, acme at 5) and brand sideways counts `{acme=1, other=1}`, as `searcher.count(ddq)` and concurrent DrillSideways return.
- Actual: 2 hits (docs 0 and 1; doc 1 costs 50) and brand sideways counts `{acme=2, other=2}`, with assertions on and off.

## Files

- [src/org/apache/lucene/facet/TestDrillSidewaysDim1TwoPhase.java](src/org/apache/lucene/facet/TestDrillSidewaysDim1TwoPhase.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.TestDrillSidewaysDim1TwoPhase
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestDrillSidewaysTwoPhaseDrillDown.java](TestDrillSidewaysTwoPhaseDrillDown.java)
- [fix-with-test.diff](fix-with-test.diff)
- [TestDrillSidewaysDim1TwoPhase.java](TestDrillSidewaysDim1TwoPhase.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
