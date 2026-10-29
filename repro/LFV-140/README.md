# DoubleRange and FloatRange accept the bounds [0.0, -0.0] and store them inverted, so range queries silently miss such documents and contradict each other (WITHIN [0,0] but not INTERSECTS [0,0])

Bug LFV-140 · Area: document / range fields (DoubleRange, FloatRange, their doc-values fields; facet range-on-range) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a DoubleRange or FloatRange with min 0.0 and max -0.0 (Java's negative zero, numerically equal to 0.0); the constructor accepts it, as it accepts [0.0, 0.0], so you expect queries to treat it as the point zero, or else an IllegalArgumentException like the one thrown for every other min &gt; max pair. Instead Lucene stores the bounds in its sort order, where -0.0 is below 0.0, so the stored range is inverted: the document matches newWithinQuery [0,0] and newIntersectsQuery [-1,1] but not newIntersectsQuery [0,0] or newIntersectsQuery of its own bounds. The same happens with the doc-values slow intersects query and range-on-range facet counts, and a query with bounds [0.0, -0.0] misses a document [0.0, 0.0] that newContainsQuery with the same bounds matches.

The test shows that `new DoubleRange("r", {0.0}, {-0.0})` is accepted (`0.0 > -0.0` is false) but stored inverted in sortable order, so `DoubleRange.newIntersectsQuery("r", {0.0}, {0.0})` misses the document.

## Expected vs actual

Expected count 1 (or an IllegalArgumentException from the constructor, which the test also accepts). Actual: 0.

## Files

- [src/org/apache/lucene/document/TestDoubleRangeSignedZeroBounds.java](src/org/apache/lucene/document/TestDoubleRangeSignedZeroBounds.java) (JUnit test)

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
     execute --select-class org.apache.lucene.document.TestDoubleRangeSignedZeroBounds
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/document-field-factories/](../../models/document-field-factories/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
