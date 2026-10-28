# Float taxonomy facets: if one child label aggregates to NaN, getTopChildren can return a lower-valued label in place of a higher one (a=5, b=NaN, c=10 gives top-1 [a]), because TopOrdAndFloatQueue compares values with &lt; and &gt;; a regression in 9.11.0 from #12966

Bug LFV-825 · Area: facet / taxonomy facets (TopOrdAndFloatQueue order of float-valued child labels in getTopChildren: float association and value-source facets) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You aggregate a float weight per taxonomy label (FloatAssociationFacetField, or a DoubleValuesSource such as the expression clicks / impressions) and call getTopChildren(k), documented to return 'the topN child labels'. If one label's value is NaN (a=5, b=NaN, c=10), getTopChildren(1) returns [a (5.0)] although c=10 outranks a, and getTopChildren(2) returns [b (NaN), c (10.0)], so the top-1 is not even in the top-2, whatever rank NaN should get. TopOrdAndFloatQueue.OrdAndFloat#lessThan compares with &lt; and &gt; and otherwise falls back to the ordinal, so NaN ties with every value, the order is not transitive and the heap keeps the wrong labels; #12966 (9.11.0) let NaN-valued labels into this queue and, in the same change, ordered dims with the NaN-safe Float.compare.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/facet/taxonomy/TestFloatFacetTopChildrenNaN.java](src/org/apache/lucene/facet/taxonomy/TestFloatFacetTopChildrenNaN.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/expressions/build/libs/lucene-expressions-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.taxonomy.TestFloatFacetTopChildrenNaN
```

The module(s) expressions, facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/facet-top-ordinal-queues-nan-and-special-value-order/](../../models/facet-top-ordinal-queues-nan-and-special-value-order/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
