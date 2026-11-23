# Sequential DrillSideways#search(DrillDownQuery, CollectorManager, List) can throw CollectionTerminatedException to its caller when the collector of a drill-sideways dimension declines a segment from getLeafCollector, where IndexSearcher.search with the same collector manager and a DrillSideways built with an executor return the result (run: the sandbox HistogramCollectorManager on a reader with a segment whose field infos lack the histogram field; each release from 10.2.0 to 10.6.0, branch_11x and main)

Finding LFV-1583 · Area: facet / DrillSideways, sequential mode (DrillSidewaysQuery, the driver of the drill-sideways collectors of a segment) · Priority: low · Impact: crash · Novelty: new

Finding page: [../../bugs/LFV-1583.html](../../bugs/LFV-1583.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index two segments, of which only the first has a document with the numeric doc-values field 'price', and call DrillSideways#search(DrillDownQuery, CollectorManager, List) on a DrillSideways built without an executor, with one dimension (color=red) and the sandbox HistogramCollectorManager('price', 10) as the collector manager of that dimension. You expect the search to return with the histogram {0=1}, which IndexSearcher.search returns for the same manager on the same index and which the same call returns when the DrillSideways was built with an executor. Instead the search throws CollectionTerminatedException, an unchecked exception without a stack trace, with and without assertions, and returns nothing: the histogram collector declines the segment without the field by throwing that exception from getLeafCollector, the search loop of IndexSearcher has a handler around that call for the hit collector, and DrillSidewaysQuery asks each dimension's collector for its leaf collector inside Weight#scorerSupplier, which the loop calls after and outside that handler; no wrong result is returned and nothing is written. With Lucene's own FacetsCollector the searches of the class that count facets are not this case (FacetsCollector does not throw the exception), and the one stock collector found that triggers it is a class of the sandbox module.

## Expected vs actual

- Expected: The search returns, and bucket 0 of the dimension's histogram counts 1 (`{0=1}`), as `IndexSearcher.search` with the same collector manager returns on the same index and as the same call returns on a `DrillSideways` built with an executor.
- Actual: `DrillSideways#search` throws `org.apache.lucene.search.CollectionTerminatedException` (no message, no stack trace) and returns nothing, with assertions on and off.

## Files

- [src/repro/TestSequentialDrillSidewaysCollectorDeclinesSegment.java](src/repro/TestSequentialDrillSidewaysCollectorDeclinesSegment.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): reproduced, with assertions on and off.
- Tested on Lucene branch_11x 262e019500ec (a commit of 2026-11-22): reproduced, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- Not new in 11.0: the test fails the same way with the 10.6.0 release jars, on a build of branch_11x (262e019500ec) and on Lucene main, with and without assertions. The limits of the finding: the failure is loud (an unchecked exception without a stack trace; no wrong result is returned and nothing is written); with Lucene's own FacetsCollector the searches of DrillSideways that count facets are not this case, because FacetsCollector does not throw the exception; the one stock collector found that triggers it is a class of the sandbox module; and the same call on a DrillSideways built with an executor returned the result in the runs. What the test does: it writes two segments of one document each (NoMergePolicy keeps them apart), both documents with color=red and only the first with the numeric doc-values field 'price' (value 5); it builds a DrillDownQuery with the one dimension color=red and no base query and calls the three-argument DrillSideways#search on a DrillSideways built without an executor, with TopScoreDocCollectorManager(10, 10) for the hits and the sandbox HistogramCollectorManager('price', 10) for the dimension; it expects the count 1 in bucket 0 of the dimension's histogram. The search call throws org.apache.lucene.search.CollectionTerminatedException, so the test's assertion is not reached; the exception carries no stack trace, and the run prints its name with the seed line and no frame of Lucene. At main b5a4546a5964 the histogram collector throws it from getLeafCollector for a segment whose field infos lack the field (HistogramCollector.java:73), and DrillSidewaysQuery asks each dimension's collector for its leaf collector with no handler for the exception (DrillSidewaysQuery.java:168). What the javadoc says at that commit: the search method's, 'Search using DrillDownQuery with custom collectors. This method can be used with any {@link CollectorManager}s.' (DrillSideways.java:398-399), and the exception's, 'Throw this exception in {@link LeafCollector#collect(int)} to prematurely terminate collection of the current leaf.' and 'Note: IndexSearcher swallows this exception and never re-throws it.' (CollectionTerminatedException.java:20-23); the exception's javadoc names LeafCollector#collect(int) and IndexSearcher, and neither getLeafCollector nor DrillSideways. The release ranges differ by the trigger. With stock classes only, as in this test, the first release is 10.2.0, the first with the sandbox histogram collector: the test fails on each release from 10.2.0 to 10.6.0 and does not compile on 10.1.0. With a collector of the caller's own that declines a segment from getLeafCollector, in tests that are not shipped here, the call threw on each build it was run on: 9.12.0 (the first release with the three-argument search), 9.12.1, 9.12.2, 9.12.3, 10.0.0, 10.1.0, 10.2.0, 10.5.2, 10.6.0, branch_11x and main. Related findings of this list, the same kind of omission at two other drivers of collectors: LFV-154 (CachingCollector#replay) and LFV-1581 (the sandbox PostCollectionFaceting). The test's input has no randomness; the test framework draws its environment from the seed. On a branch_11x checkout set V=11.0.0-SNAPSHOT; for a release, set V to its version and use the six jars of that release from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework, lucene-facet, lucene-queries, lucene-sandbox) in place of the six $LUCENE jars. For the Gradle alternative at the end of this page the module is lucene/sandbox, whose tests have the facet module on their class path: with the test copied to lucene/sandbox/src/test/repro/ it reports 1 test completed, 1 failed, with the same exception, on branch_11x 262e019500ec and on main b5a4546a5964, with -Ptests.asserts=true and with -Ptests.asserts=false.
- This finding was re-run on Lucene main 11c8b6ea99ae on 2026-11-23: still reproduces.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with org.apache.lucene.search.CollectionTerminatedException, thrown out of the test's DrillSideways#search call (the exception carries no stack trace, so the run names no frame of Lucene).
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
java -ea -esa -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class repro.TestSequentialDrillSidewaysCollectorDeclinesSegment
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestSequentialDrillSidewaysCollectorDeclinesSegment.java](TestSequentialDrillSidewaysCollectorDeclinesSegment.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
