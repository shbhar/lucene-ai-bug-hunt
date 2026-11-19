# Luke: a sorted search ignores exactHitsCount, so a sorted Search-tab query with more than 1,000 hits shows a lower-bound total ('1001+ hits') although the Sort tab forces 'exact hits count' on

Finding LFV-1036 · Area: luke / Search model, sorted search (SearchImpl.search) behind the Luke Search tab · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1036.html](../../bugs/LFV-1036.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open an index in Luke, pick a sort field in the Sort tab (Luke then ticks and greys out 'exact hits count') and run a search that matches more than 1,000 documents; you expect the exact total, as the javadoc of the Sort overload of Search.search promises ('if set to true, the exact total hits count is returned'). Instead Luke shows a lower bound such as '1001+ hits', and there is no way to get the exact count of a sorted search. The cause is that SearchImpl.search() passes exactHitsCount only to the unsorted collector; the sorted branch calls IndexSearcher.searchAfter(after, query, pageSize, sort), which counts exactly only up to 1,000 hits.

## Expected vs actual

- Expected: `TotalHits(1001, EQUAL_TO)`, shown in Luke as "1001 hits".
- Actual: `AssertionError: expected:<1001 hits> but was:<1001+ hits>`, with assertions on and off, on main and 10.6.0.

## Files

- [src/org/apache/lucene/luke/models/search/TestSortedSearchExactHitsCount.java](src/org/apache/lucene/luke/models/search/TestSortedSearchExactHitsCount.java) (JUnit test)

## Run it

- Tested on Lucene main 1ae70c6e45b8 (a commit of 2026-11-19): still reproduces (the repro adapted to an API change on this commit). The change: #16962 made the no-argument MatchAllDocsQuery constructor private on main (24d9e7d1a0d) and on the 11.0 branch (branch_11x, a6a26f9b251), so replace new MatchAllDocsQuery() with MatchAllDocsQuery.INSTANCE before you compile the test there.
- Tested on Lucene main 645d4fe14a20 (a commit of 2026-10-28): reproduced.
- The class path below names six Lucene jars: Luke's search model loads classes of lucene-queryparser and lucene-analysis-common, so the test needs these two beside lucene-luke; it needs no third-party jar. The constant of the replacement named above exists from Lucene 10.4.0 on. With the jars of a release from 9.1.0 to 10.6.0 the test compiles as shipped: for such a release, put the six jars of that release (Maven Central, org.apache.lucene) in place of the six $LUCENE jars. Run on 2026-11-17, with assertions on and off: with the replacement the test fails 1 of 1 on a build of main 9965d34e3c77 and of the 11.0 branch (branch_11x 9d2b5a15427a; there V=11.0.0-SNAPSHOT), and as shipped it fails 1 of 1 with the 10.6.0 jars, each time with the message below.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.AssertionError: expected:&lt;1001 hits&gt; but was:&lt;1001+ hits&gt;; a NoClassDefFoundError means that a jar is missing from the class path and is not the finding.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/luke/build/libs/lucene-luke-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar   # Luke's search model needs these two
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.luke.models.search.TestSortedSearchExactHitsCount
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestSortedSearchExactHitsCount.java](TestSortedSearchExactHitsCount.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
