# Sandbox DrillSidewaysFacetOrchestrator#collect(query, drillSideways, mainCollector) returns the internal MultiCollectorManager's Object[] instead of the main collector manager's result: with a TopScoreDocCollectorManager, assigning the result to TopDocs throws ClassCastException (run also with a hit-count and a sorted top-docs manager; an experimental class; 10.2.0 to 10.6.0, branch_11x and main)

Finding LFV-1541 · Area: sandbox / facets (the facet builder utilities: DrillSidewaysFacetOrchestrator#collect with a main collector manager, over the facet module's DrillSideways) · Priority: low · Impact: minor · Novelty: new

Finding page: [https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1541.html](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1541.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You ask the sandbox drill-sideways helper for hits and facets in one call: TopDocs hits = new DrillSidewaysFacetOrchestrator().addDrillSidewaysBuilder("price", builder).collect(query, drillSideways, new TopScoreDocCollectorManager(1, 1)). You expect the top hits, because the method is declared to return the result of the collector manager you pass and the sibling FacetOrchestrator#collect does so. Instead the method returns the Object[] of its internal MultiCollectorManager (the hits are its element 0, in this call its only element), so the assignment throws ClassCastException: Object[] cannot be cast to TopDocs; the facet counts collected by the same call are right.

## Expected vs actual

- Expected: `hits` is the `TopDocs` of the collector manager that was passed: `hits.totalHits.value()` is 1.
- Actual: `java.lang.ClassCastException: class [Ljava.lang.Object; cannot be cast to class org.apache.lucene.search.TopDocs` at the test's line 52, with assertions on and off.

## Files

- [src/org/apache/lucene/sandbox/facet/utils/TestDrillSidewaysOrchestratorMainResult.java](src/org/apache/lucene/sandbox/facet/utils/TestDrillSidewaysOrchestratorMainResult.java) (JUnit test)

## Run it

- Tested on Lucene main 82cf7465bb8f (a commit of 2026-11-26): still reproduces.
- Tested on Lucene branch_11x 89e68e0f3a50 (a commit of 2026-11-17): reproduced.
- A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with a java.lang.ClassCastException whose message begins 'class [Ljava.lang.Object; cannot be cast to class org.apache.lucene.search.TopDocs', thrown at line 52 of the test, the collect call, before the test's assertion is reached.
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
     execute --select-class org.apache.lucene.sandbox.facet.utils.TestDrillSidewaysOrchestratorMainResult
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Files shown on the finding page

- [TestDrillSidewaysOrchestratorMainResult.java](TestDrillSidewaysOrchestratorMainResult.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
