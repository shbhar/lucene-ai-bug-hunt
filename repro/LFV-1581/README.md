# Sandbox PostCollectionFaceting#collect can throw CollectionTerminatedException to its caller when a collector it replays into ends a segment early, where IndexSearcher.search with the same collector manager returns the result (run: the sandbox HistogramCollectorManager on a reader with a segment whose field infos lack the histogram field; an experimental class; each release from 10.4.0 to 10.6.0, branch_11x and main)

Finding LFV-1581 · Area: sandbox / facets: the facet utilities (experimental), PostCollectionFaceting (the replay loop of two-step faceting) · Priority: low · Impact: crash · Novelty: new

Finding page: [../../bugs/LFV-1581.html](../../bugs/LFV-1581.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index two segments, of which only the first has a document with the numeric doc-values field 'price', collect the hits of a match-all search into a FacetsCollector and replay them with the sandbox class PostCollectionFaceting into a HistogramCollectorManager('price', 10). You expect the histogram {0=1}, which IndexSearcher.search returns for the same manager on the same index. Instead collect() throws CollectionTerminatedException, an unchecked exception without a stack trace, with and without assertions: the histogram collector declines the segment without the field by throwing that exception from getLeafCollector, the search loop of IndexSearcher catches it and goes on with the next segment, and the replay loop has no such handler (nor one around collect), so the exception reaches the caller and no histogram is returned.

## Expected vs actual

- Expected: The histogram `{0=1}`: `collect().drillDownResult().get(0)` is 1, as `IndexSearcher.search` with the same collector manager returns on the same index.
- Actual: `collect()` throws `org.apache.lucene.search.CollectionTerminatedException` (no message, no stack trace) and returns no histogram, with assertions on and off.

## Files

- [src/org/apache/lucene/sandbox/facet/utils/TestPostCollectionFacetingEndsLeafEarly.java](src/org/apache/lucene/sandbox/facet/utils/TestPostCollectionFacetingEndsLeafEarly.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): reproduced, with assertions on and off.
- Tested on Lucene branch_11x 262e019500ec (a commit of 2026-11-22): reproduced, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- Not new in 11.0: the test fails the same way with the 10.6.0 release jars, on a build of branch_11x (262e019500ec) and on Lucene main (b5a4546a5964), with and without assertions. What the test does: it writes two segments of one document each (NoMergePolicy keeps them apart), of which only the first document has the numeric doc-values field 'price' (value 5); it collects the hits of a match-all search into a FacetsCollector and replays them through PostCollectionFaceting into HistogramCollectorManager('price', 10); it expects the count 1 in bucket 0. The collect() call throws org.apache.lucene.search.CollectionTerminatedException, so the test's assertion is not reached; the exception carries no stack trace, and the run prints its name with the seed line and no frame of Lucene. The test in its first form (the same scenario with a second assertion, on the size of the histogram) fails the same way on each release from 10.4.0 to 10.6.0, on branch_11x and on main; the shipped form fails the same way on 10.6.0, on branch_11x 262e019500ec and on main b5a4546a5964 and, in later runs of 2026-11-22 and of 2026-11-23, with the release jars of 10.4.0, 10.5.0, 10.5.1 and 10.5.2, each with and without assertions. The same omission in CachingCollector#replay is the finding LFV-154 of this list. The test's input has no randomness; the test framework draws its environment from the seed. On a branch_11x checkout set V=11.0.0-SNAPSHOT; for a release, set V to its version (10.4.0, 10.5.0, 10.5.1, 10.5.2 or 10.6.0) and use the six jars of that release from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework, lucene-facet, lucene-queries, lucene-sandbox) in place of the six $LUCENE jars. For the Gradle alternative at the end of this page the module is lucene/sandbox: with the test copied to lucene/sandbox/src/test/org/apache/lucene/sandbox/facet/utils/ it reports 1 test completed, 1 failed, with the same exception, on branch_11x 262e019500ec and on main b5a4546a5964, with -Ptests.asserts=true and with -Ptests.asserts=false.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with org.apache.lucene.search.CollectionTerminatedException, thrown out of the test's PostCollectionFaceting#collect call (the exception carries no stack trace, so the run names no frame of Lucene).
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
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.sandbox.facet.utils.TestPostCollectionFacetingEndsLeafEarly
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestPostCollectionFacetingEndsLeafEarly.java](TestPostCollectionFacetingEndsLeafEarly.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
