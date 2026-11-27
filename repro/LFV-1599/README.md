# Test suite on branch_11x and main (both unreleased): BaseVectorSimilarityQueryTestCase.testVectorsAboveSimilarity has been declared without 'public' since #14049, and Lucene's test runner takes a test*-named method without @Test only when it is public, so the method is not run in TestFloatVectorSimilarityQuery, TestByteVectorSimilarityQuery and TestFloat16VectorSimilarityQuery; the 10.6.0 test sources have it public and run it (run on the 10.6.0 release jars for TestFloatVectorSimilarityQuery and TestByteVectorSimilarityQuery, the two subclasses of the base class at the 10.6.0 tag)

Finding LFV-1599 · Area: test sources / test-method discovery (core search: the vector similarity query tests, BaseVectorSimilarityQueryTestCase and its three subclasses) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run Lucene's own test suite on branch_11x or main, and you expect the test testVectorsAboveSimilarity of the vector similarity queries to run in TestFloatVectorSimilarityQuery, TestByteVectorSimilarityQuery and TestFloat16VectorSimilarityQuery, as it runs in the 10.6.0 sources (in the first two classes; the third does not exist at the 10.6.0 tag): it is the test that indexes random vectors, asks for the documents at or above a score that one of them reaches, and checks which documents come back and with what scores. Instead it does not run and the test run says nothing about it: since #14049 (merged to main on 2024-12-09, in no release) the method is declared 'void testVectorsAboveSimilarity()' without 'public' and has no @Test annotation, and Lucene's test runner takes a method that is named test* and has no @Test only when it is public. Nothing in Lucene itself is wrong in what ran (with the one word 'public' restored the test is listed, runs and passes); what the two unreleased lines lack is a check that the 10.6.0 sources have.

## Expected vs actual

- Expected: You expect the test testVectorsAboveSimilarity of the vector similarity queries to run in TestFloatVectorSimilarityQuery, TestByteVectorSimilarityQuery and TestFloat16VectorSimilarityQuery, as it runs in the 10.6.0 sources (in the first two classes; the third does not exist at the 10.6.0 tag): it is the test that indexes random vectors, asks for the documents at or above a score that one of them reaches, and checks which documents come back and with what scores.
- Actual: It does not run and the test run says nothing about it: since #14049 (merged to main on 2024-12-09, in no release) the method is declared 'void testVectorsAboveSimilarity()' without 'public' and has no @Test annotation, and Lucene's test runner takes a method that is named test* and has no @Test only when it is public.

## Files

- [src/org/apache/lucene/search/TestVectorsAboveSimilarityRuns.java](src/org/apache/lucene/search/TestVectorsAboveSimilarityRuns.java) (JUnit test)

## Run it

- Tested on Lucene main 77d56fefb355 (a commit of 2026-11-25): reproduced on 2026-11-26, with assertions on and off, with the test sources of this commit.
- Tested on Lucene branch_11x 4e8fd3dc3bc4 (a commit of 2026-11-25): reproduced on 2026-11-26, with assertions on and off, with the test sources of this commit (11.0.0-SNAPSHOT).
- Tested on Lucene main 8506598d7a98 (a commit of 2026-11-25): reproduced, with assertions on and off, with the test sources of this commit.
- Tested on Lucene branch_11x 55ef56a69344 (a commit of 2026-11-25): reproduced, with assertions on and off, with the test sources of this commit (11.0.0-SNAPSHOT).
- In no release: the test fails on Lucene main (8506598d7a98 and 77d56fefb355) and on branch_11x (55ef56a69344 and 4e8fd3dc3bc4), both unreleased when read on 2026-11-26, with and without assertions, and passes with the 10.6.0 release jars and the test sources of the 10.6.0 tag, where the method is public. A finding about Lucene's own test sources: no shipped class, jar, API or index is involved, no test of TestFloatVectorSimilarityQuery, TestByteVectorSimilarityQuery or TestFloat16VectorSimilarityQuery failed when the three classes ran on branch_11x 8a72db074350 and main 1640b5c394de (54 tests found, 51 passed, 3 skipped, with assertions on and off), and with the one word 'public' restored the method ran and passed in the runs made for this finding. What the test does: it is a test about tests. It runs nothing of Lucene's suite: it asks Lucene's JUnit runner for the list of tests of TestFloatVectorSimilarityQuery and expects testVectorsAboveSimilarity, a method of the base class BaseVectorSimilarityQueryTestCase, to be among them. It fails with java.lang.AssertionError: the runner lists 18 test methods and no testVectorsAboveSimilarity, followed by the 18 names, because the method is declared without 'public' and has no @Test annotation (BaseVectorSimilarityQueryTestCase.java:494 on main 77d56fefb355 and on branch_11x 4e8fd3dc3bc4), and Lucene's runner takes a method that is named test* and has no @Test only when it is public. What the unrun method would test: it indexes random vectors, asks for the documents at or above a similarity that one of them reaches, and checks which documents come back and with what scores. The class under test is a Lucene test source and is in none of the jars that the block names, so the block compiles it, unchanged, from the checkout together with its base class and two more test sources that they use; the test is evidence for this finding, not a regression test to keep. Its outcome does not depend on the seed; run it once, without -Dtests.iters. A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there. For the control on the release, take a checkout of the tag releases/lucene/10.6.0 as $LUCENE (its test sources have 'public void' at line 483 of the base class), set V=10.6.0 and use the three 10.6.0 jars from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework) in place of the three $LUCENE jars: the run then reports 1 test successful, with and without assertions. For the Gradle alternative at the end of this page the module is lucene/core: with the test copied to lucene/core/src/test/org/apache/lucene/search/ it reports 1 test completed, 1 failed, with the same message, on branch_11x 55ef56a69344 and on main 8506598d7a98, with -Ptests.asserts=true and with -Ptests.asserts=false.
- This finding was re-run on Lucene main 82cf7465bb8f on 2026-11-26: still reproduces.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.AssertionError: the runner lists 18 test methods and no testVectorsAboveSimilarity; no test of Lucene's own suite is run or fails, and on test sources that declare the method public the run reports 1 test successful.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

# the class under test, TestFloatVectorSimilarityQuery, is a Lucene test source and is in no jar: it is compiled,
# unchanged, from the checkout, with its base class and the two other test sources that they use
T=$LUCENE/lucene/core/src/test/org/apache/lucene
javac -d classes -cp "$CP" $(find src -name '*.java') $T/search/TestFloatVectorSimilarityQuery.java $T/search/BaseVectorSimilarityQueryTestCase.java $T/util/TestVectorUtil.java $T/internal/vectorization/BaseVectorizationTestCase.java

# assertions on
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=4 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestVectorsAboveSimilarityRuns
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
