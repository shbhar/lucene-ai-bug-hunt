# SortedSetDocValuesFacetCounts and ConcurrentSortedSetDocValuesFacetCounts count a FacetsCollector that visited exactly one segment of a multi-segment reader in that segment's ordinals, so its counts can land on other labels with no error; the merged collector of the stock FacetsCollectorManager counted right in the runs

Finding LFV-1578 · Area: facet / sorted-set doc-values facet counts (the choice of the ordinal map in count() of SortedSetDocValuesFacetCounts and ConcurrentSortedSetDocValuesFacetCounts) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1578.html](../../bugs/LFV-1578.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search a six-segment index (one document a segment: a=x in the first five, a=z in the sixth) with an IndexSearcher that has an executor, give each slice of the search its own stock FacetsCollector through your own CollectorManager, count each collector with SortedSetDocValuesFacetCounts and add the counts. You expect x=5 z=1, which the merged collector of the stock FacetsCollectorManager gives on the same searcher. Instead the sum is x=6 z=0, with no error, with and without assertions. The collector of the second slice visited one segment, and for a list of exactly one per-segment record the counter leaves out the reader's ordinal map ('matchingDocs.size() &gt; 1') and uses the segment's own ordinal 0 (z, its only label) as the reader's ordinal 0 (x); with the documents in the other order (z first) the same search counts right, because the segment's ordinals then agree with the reader's.

## Expected vs actual

- Expected: x=5 z=1: the six hits carry x five times and z once, and the merged collector of the stock `FacetsCollectorManager` counts that on the same searcher.
- Actual: x=6 z=0 (`org.junit.ComparisonFailure: expected:<x=[5 z=1]> but was:<x=[6 z=0]>` at line 78), with no exception from Lucene, assertions on and off.

## Files

- [src/org/apache/lucene/facet/sortedset/TestSortedSetFacetCountsPerSliceCollector.java](src/org/apache/lucene/facet/sortedset/TestSortedSetFacetCountsPerSliceCollector.java) (JUnit test)

## Run it

- Tested on Lucene main 11c8b6ea99ae (a commit of 2026-11-23): still reproduces.
- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): reproduced.
- Not new in 11.0: the test fails the same way with the 10.6.0 release jars, on a build of branch_11x (262e019500ec) and on Lucene main (b5a4546a5964), with and without assertions. What the test does: it indexes six documents with a commit after each and NoMergePolicy, which gives six one-document segments (the facet label a=x in the first five, a=z in the sixth), and searches them with a query that matches every document through IndexSearcher.search(Query, CollectorManager), on a searcher whose executor runs each slice on the calling thread; default slicing makes two slices here, the first five segments and the sixth. Its own CollectorManager gives each slice a stock FacetsCollector, counts each collector by itself with SortedSetDocValuesFacetCounts and adds the counts; the test expects x=5 z=1. The run reports 1 test found and 1 test failed, with org.junit.ComparisonFailure: expected:&lt;x=[5 z=1]&gt; but was:&lt;x=[6 z=0]&gt;; Lucene itself throws no exception. The commands below print one run, with assertions off; with -ea -esa in place of -da -dsa -Dtests.asserts=false the outcome is the same. The test has no random() call of its own; the test framework draws its environment (the codec and the directory among other things) from the seed, and the outcome was the same under each seed that was run. On a branch_11x checkout set V=11.0.0-SNAPSHOT; for the release, set V=10.6.0 and use the four 10.6.0 jars from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework, lucene-facet) in place of the four $LUCENE jars. For the Gradle alternative at the end of this page the module is lucene/facet: with the test copied to lucene/facet/src/test/org/apache/lucene/facet/sortedset/ it reports 1 test completed, 1 failed, with the same message, on branch_11x 262e019500ec and on main b5a4546a5964, with -Ptests.asserts=true and with -Ptests.asserts=false.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.sortedset.TestSortedSetFacetCountsPerSliceCollector
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestSortedSetFacetCountsPerSliceCollector.java](TestSortedSetFacetCountsPerSliceCollector.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
