# On Lucene 10.3.0 to 10.6.0 (on 10.3.x only until the query has been used in a search), toString() of the query built by RescoreTopNQuery.createFullPrecisionRescorerQuery throws NullPointerException, because the value source prints the name of a similarity function it was built without (released 10.x line only; 11.0 and main are not affected)

Finding LFV-1527 · Area: core / search: full-precision vector rescoring (FullPrecisionFloatVectorSimilarityValuesSource.toString, reached from RescoreTopNQuery.toString and DoubleValuesSource.explain) · Priority: low · Impact: crash · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

Fixed upstream: [#16628](https://github.com/apache/lucene/pull/16628) (commit [55fd2e75fb2b](https://github.com/apache/lucene/commit/55fd2e75fb2be51ab46f16c3fd71dc00a8152523)), merged 2026-09-28; also on the 11.0 release branch (the same commit). On the released 10.x line only (10.3.0 to 10.6.0): main no longer had the failing call when the finding was listed.

## What it shows

You build a rescoring query with RescoreTopNQuery.createFullPrecisionRescorerQuery(innerQuery, targetVector, "vector", 3), the convenience factory that lets the field's own similarity function score the full-precision vectors, and print it with toString(), for example in a log line; you expect a description of the query. Instead, on Lucene 10.3.0 to 10.6.0 (on 10.3.0 to 10.3.2 only until the query has been used in a search), toString() throws NullPointerException ('Cannot invoke "org.apache.lucene.index.VectorSimilarityFunction.name()" because "this.vectorSimilarityFunction" is null'): the factory builds its value source without a similarity function, which that class documents as the way to use the field's configured one, and the value source's toString() calls name() on the missing function. Searching with the query works and returns the right hits; on 10.4.0 to 10.6.0 IndexSearcher.explain of a hit scored through a value source built the same way throws the same exception; the 11.0 branch and main print the query (the function shows as 'null'), so only the released 10.x line is affected.

## Expected vs actual

- Expected: You expect a description of the query.
- Actual: On Lucene 10.3.0 to 10.6.0 (on 10.3.0 to 10.3.2 only until the query has been used in a search), toString() throws NullPointerException ('Cannot invoke "org.apache.lucene.index.VectorSimilarityFunction.name()" because "this.vectorSimilarityFunction" is null'): the factory builds its value source without a similarity function, which that class documents as the way to use the field's configured one, and the value source's toString() calls name() on the missing function.

## Files

- [src/org/apache/lucene/search/TestFullPrecisionRescorerQueryToString.java](src/org/apache/lucene/search/TestFullPrecisionRescorerQueryToString.java) (JUnit test)

## Run it

- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- Tested on Lucene 10.3.0 (e2871287e4a3) (a commit of 2025-09-05): reproduced, with assertions on and off, on the 10.3.0 release jars, the first affected release, which are built from this commit (the tag releases/lucene/10.3.0).
- Tested on Lucene main 9965d34e3c77 (a commit of 2026-11-16): the test passes, with assertions on and off: main does not have the failing call.
- Tested on Lucene branch_11x 9d2b5a15427a (a commit of 2026-11-16): the test passes, with assertions on and off (11.0.0-SNAPSHOT): the 11.0 branch does not have the failing call.
- This finding is in the released 10.x line only. The test fails with the jars of every release from 10.3.0 to 10.6.0 and passes on Lucene main and on the 11.0 release branch (branch_11x), which no longer have the failing call (since #16628): 11.0 is not affected. The commands below run the test first on the 10.6.0 release jars, where it fails, and then, as the control, on a build of main or of branch_11x, where it passes. For the first run put lucene-core-10.6.0.jar, lucene-codecs-10.6.0.jar and lucene-test-framework-10.6.0.jar (Maven Central, org.apache.lucene) into your copy of this folder, next to the four test jars that the commands name; for another release set V (10.3.0, the first affected release, was run the same way and fails the same way). For a run without assertions put -da -dsa -Dtests.asserts=false in place of -ea -esa: the outcomes are the same. What the test does: it builds a query with RescoreTopNQuery.createFullPrecisionRescorerQuery over a KnnFloatVectorQuery and asserts that query.toString() returns a text; it needs no index, and its input has no randomness. On 10.3.0 to 10.6.0 toString() throws java.lang.NullPointerException: Cannot invoke "org.apache.lucene.index.VectorSimilarityFunction.name()" because "this.vectorSimilarityFunction" is null. Not covered by this test and run separately: on 10.3.0 to 10.3.2 the same query prints once it has been used in a search (run on 10.3.0 and 10.3.2); on 10.4.0 to 10.6.0 it throws before and after a search, and there IndexSearcher.explain of a hit of a FunctionScoreQuery scored by a value source built the same way throws the same exception (run on 10.4.0 and 10.6.0). Known outside Lucene only: three projects that use the API described it in public (a neo4j commit message, the description of a Solr pull request and a code comment in nrtsearch); no Lucene issue, pull request or list thread was found that describes it. For the Gradle alternative at the end of this page the module is lucene/core and the checkout must be of a release tag from 10.3.0 to 10.6.0 (run on the tag releases/lucene/10.6.0); in a checkout of main or of branch_11x the test passes.
- This finding was re-run on Lucene main f2b061406c4b on 2026-11-19: the test passed, as recorded before (a control run).
- How to read the outcome: on the jars of a release from 10.3.0 to 10.6.0 a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.NullPointerException: Cannot invoke "org.apache.lucene.index.VectorSimilarityFunction.name()" because "this.vectorSimilarityFunction" is null, thrown from FullPrecisionFloatVectorSimilarityValuesSource.toString; on a build of main or of branch_11x the same test passes (1 test successful), which is what the finding states for the unreleased lines.

```bash
# needs JDK 25 on the PATH; run in a copy of this folder that also holds the jars named below (Maven Central)
V=10.6.0   # the release under test: any release from 10.3.0 to 10.6.0
T=randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies
T=$T:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher
CP=lucene-core-$V.jar:lucene-codecs-$V.jar:lucene-test-framework-$V.jar:$T   # org.apache.lucene, release $V

javac -d classes -cp "$CP" $(find src -name '*.java')

# the finding: on the release jars the test fails (1 test failed, NullPointerException)
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestFullPrecisionRescorerQueryToString

# the control: a Lucene checkout of main or of branch_11x, built with: ./gradlew --no-daemon assemble
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT   # on branch_11x: V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar:$T

javac -d classes-unreleased -cp "$CP" $(find src -name '*.java')

# there the same test passes (1 test successful)
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-unreleased:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestFullPrecisionRescorerQueryToString
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
