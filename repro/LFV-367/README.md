# DrillDownQuery.clone shares the per-dimension BooleanQuery.Builder objects with the original, so adding a value to a dimension both copies already constrain changes what the other copy matches (released since 4.7.0)

Bug LFV-367 · Area: facet / DrillDownQuery (clone and the per-dimension BooleanQuery.Builder state) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-367.html](../../bugs/LFV-367.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a DrillDownQuery that restricts color=red, call clone, and add color=blue to the clone (or to the original), expecting the copy you did not touch to keep matching only red, as clone and the upstream testClone imply. Instead both copies match red OR blue: the clone shares each dimension's internal BooleanQuery.Builder with its source, and add on an existing dimension appends a SHOULD clause to that shared builder in place. Since 9.0 a copy that was already searched keeps a cached snapshot and shows the leaked value only after its own dimension changes again (then 3 hits where 2 are correct), and the two copies stay equals with the same hashCode while they match different documents.

## Expected vs actual

- Expected: The untouched copy keeps matching red only (1 document); a searched copy matches red or green (2) after its own add; copies that match 1 and 2 documents are not equal.
- Actual: The untouched copy matches 2 in both directions (`expected:<1> but was:<2>`), the searched copy matches 3 (`expected:<2> but was:<3>`), and the two copies compare equal.

## Files

- [src/org/apache/lucene/facet/TestDrillDownQueryCloneShares.java](src/org/apache/lucene/facet/TestDrillDownQueryCloneShares.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.TestDrillDownQueryCloneShares
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestDrillDownQueryCloneIsolation.java](TestDrillDownQueryCloneIsolation.java)
- [TestDrillDownQueryCloneShares.java](TestDrillDownQueryCloneShares.java)
- [TestDrillDownQueryCloneIndependence.java](TestDrillDownQueryCloneIndependence.java)
- [DrillDownCloneStateMain.java](DrillDownCloneStateMain.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
