# Sandbox QueryProfilerIndexSearcher: after a query whose rewrite calls the searcher's rewrite again, getRewriteTime() includes a System.nanoTime() reading instead of the outer rewrite's elapsed time, and with assertions the profiled search throws AssertionError (run with a KnnFloatVectorQuery whose filter is a TermQuery; 9.7.0 to 10.6.0, branch_11x and main; known outside Lucene)

Finding LFV-1544 · Area: sandbox / search: the query profiler (QueryProfilerIndexSearcher, QueryProfilerTree: the reported rewrite time) · Priority: low · Impact: minor · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open a QueryProfilerIndexSearcher (sandbox module) over an index with one document, search it once with a KnnFloatVectorQuery whose filter is a TermQuery, and read getRewriteTime(), documented as the 'total time taken to rewrite all queries in this profile'. You expect a duration that is at most the time of the search (at most twice that, if the filter's rewrite is counted by itself as well). Run without assertions, the search returns its hit and getRewriteTime() was about 4.2e14 ns for a search of 1e7 to 2e7 ns in the runs here; run with assertions, the search throws AssertionError from QueryProfilerTree.startRewriteTime. The profiler keeps one start time for the rewrites in flight: the vector query rewrites its filter through the searcher from inside its own rewrite, the inner stop sets the start time to 0, and the outer stop then adds System.nanoTime() minus 0. Not new in 11: 10.6.0 fails the same test; 9.7.0 is the first release on which it fails and 9.6.0 passes.

## Expected vs actual

- Expected: You expect a duration that is at most the time of the search (at most twice that, if the filter's rewrite is counted by itself as well).
- Actual: While the reported behaviour is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/sandbox/search/TestQueryProfilerNestedRewriteTime.java](src/org/apache/lucene/sandbox/search/TestQueryProfilerNestedRewriteTime.java) (JUnit test)

## Run it

- Tested on Lucene main 7b8418faa2ff (a commit of 2026-11-20): still reproduces.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.sandbox.search.TestQueryProfilerNestedRewriteTime
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
