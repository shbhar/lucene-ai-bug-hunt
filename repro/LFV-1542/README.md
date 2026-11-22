# DrillSidewaysFacetOrchestrator#addDrillSidewaysBuilder takes a dimension name but pairs the builder with a dimension of the query by the order in which the names were first registered: sideways builders registered in another order than the query's dimensions can silently return their counts over another dimension's documents (sandbox, experimental; 10.2.0 to 10.6.0, branch_11x and main; known upstream, #16707)

Finding LFV-1542 · Area: sandbox / facets: the facet builder utilities (experimental), DrillSidewaysFacetOrchestrator over DrillSideways · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index three documents with the facet dimensions brand and color (A/red, A/blue, B/red), build a DrillDownQuery with brand=A added first and color=red second, and register two builders on a DrillSidewaysFacetOrchestrator: addDrillSidewaysBuilder("color", colorBuilder) first, addDrillSidewaysBuilder("brand", brandBuilder) second. You expect the brand builder to count brand over the two red documents (A=1, B=1), since the method takes the dimension's name and no javadoc states an order. You get A=2 for brand and red=2 for color, with no error and the right hits: the name is only turned into a position (the number of other names registered before it), and DrillSideways gives the builders at position i the documents of the query's i-th dimension, so here each of the two builders counted its labels over the other dimension's documents.

## Expected vs actual

- Expected: You expect the brand builder to count brand over the two red documents (A=1, B=1), since the method takes the dimension's name and no javadoc states an order.
- Actual: While the reported behaviour is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/sandbox/facet/utils/TestDrillSidewaysBuilderRegistrationOrder.java](src/org/apache/lucene/sandbox/facet/utils/TestDrillSidewaysBuilderRegistrationOrder.java) (JUnit test)

## Run it

- Tested on Lucene main 1a59f96fc6de (a commit of 2026-11-21): still reproduces.
- Tested on Lucene branch_11x 89e68e0f3a50 (a commit of 2026-11-17): reproduced.
- A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there.
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
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.sandbox.facet.utils.TestDrillSidewaysBuilderRegistrationOrder
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
