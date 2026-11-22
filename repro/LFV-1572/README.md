# lucene-analysis-common exports the new Albanian package org.apache.lucene.analysis.sq but does not open it to org.apache.lucene.core: when Lucene runs as named modules, ModuleResourceLoader, and ClasspathResourceLoader built from a class of that module, throw IOException for the stop-word file that the jar ships; new AlbanianAnalyzer() and the class path are not affected (main and branch_11x, both unreleased; new code of 11.0)

Finding LFV-1572 · Area: analysis / lucene-analysis-common module descriptor (the word-list packages that are opened to org.apache.lucene.core for the resource loaders) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run Lucene as named modules (the jars on the module path) and build a CustomAnalyzer with new ModuleResourceLoader(&lt;the module of lucene-analysis-common&gt;) and a stop filter whose words file is org/apache/lucene/analysis/sq/stopwords.txt, the Albanian stop-word list that the jar ships since #16957. You expect the chain to build and to drop a listed word, as it does on the class path and as the same chain does for the Bulgarian list on the module path: since 9.1.0 the module opens its packages that hold a word-list file to org.apache.lucene.core so that ModuleResourceLoader and ClasspathResourceLoader can read them (LUCENE-10348, by Uwe Schindler), and Lucene's check TestModularLayer.testAllOpenAnalysisPackagesInSync states that rule. Instead the builder throws IOException 'Resource not found: org/apache/lucene/analysis/sq/stopwords.txt': the module descriptor exports the new package and has no opens clause for it (27 packages of the jar hold a word-list file, 26 are opened), so the loaders, which behave as documented for a package that is not opened, cannot read the file; new AlbanianAnalyzer(), its default stop set and the class path are not affected, and no release has the package.

## Expected vs actual

- Expected: You expect the chain to build and to drop a listed word, as it does on the class path and as the same chain does for the Bulgarian list on the module path: since 9.1.0 the module opens its packages that hold a word-list file to org.apache.lucene.core so that ModuleResourceLoader and ClasspathResourceLoader can read them (LUCENE-10348, by Uwe Schindler), and Lucene's check TestModularLayer.testAllOpenAnalysisPackagesInSync states that rule.
- Actual: The builder throws IOException 'Resource not found: org/apache/lucene/analysis/sq/stopwords.txt': the module descriptor exports the new package and has no opens clause for it (27 packages of the jar hold a word-list file, 26 are opened), so the loaders, which behave as documented for a package that is not opened, cannot read the file; new AlbanianAnalyzer(), its default stop set and the class path are not affected, and no release has the package.

## Files

- [src/org/apache/lucene/analysis/tests/TestAlbanianStopwordsOpenToCore.java](src/org/apache/lucene/analysis/tests/TestAlbanianStopwordsOpenToCore.java) (JUnit test)

## Run it

- Tested on Lucene main 7b8418faa2ff (a commit of 2026-11-20): reproduced with Lucene's jars on the module path (as named modules), with assertions on and off; on the class path the same test passes.
- Tested on Lucene branch_11x 3221dd4cf3a1 (a commit of 2026-11-20): reproduced on the module path, with assertions on and off (set V=11.0.0-SNAPSHOT); passes on the class path.
- This test fails only when Lucene's jars run as named modules, that is on the module path. The commands below compile the test and then start the compiled class with the jars on the module path and only the classes directory on the class path, with the java of JDK 25 (the runs used 25.0.3); for a run without assertions put -da -dsa -Dtests.asserts=false in place of -ea -esa. With the same jars on the class path the test passes. The six jars after hamcrest-3.0.jar are on Maven Central too; the module of the test framework cannot be resolved without them (the JVM then stops with a FindException). --add-modules ALL-MODULE-PATH is needed because the launch names no main module: without it the jars on the module path are not resolved and the JVM cannot find org.junit.runner.JUnitCore. The launch on the module path is the only JVM setup this repro needs, and the commands below carry it. A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there. What the test does: one statement, new ModuleResourceLoader(AlbanianAnalyzer.class.getModule()).openResource("org/apache/lucene/analysis/sq/stopwords.txt"), the loader call of the description above without the analyzer around it; a test of the whole chain (CustomAnalyzer.builder with that loader, a whitespace tokenizer and a stop filter over the same file) fails with the same exception on both builds. Control: with BulgarianAnalyzer and org/apache/lucene/analysis/bg/stopwords.txt in place of the Albanian class and file, the same commands report OK (1 test) on both builds. The outcome does not depend on the seed. For the Gradle alternative below the matching module is lucene/analysis.tests, whose tests Lucene's build runs as a named module: copy the test to lucene/analysis.tests/src/test/org/apache/lucene/analysis/tests/ and run ./gradlew -p lucene/analysis.tests test --tests TestAlbanianStopwordsOpenToCore -Ptests.seed=DEADBEEF -Ptests.asserts=true; it fails 1 of 1 with the same exception on main 7b8418faa2ff and on branch_11x 3221dd4cf3a1, and it does the same with -Ptests.asserts=false.
- This finding was re-run on Lucene main 1a59f96fc6de on 2026-11-21: still reproduces.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test run and 1 failure, with java.io.IOException: Resource not found: org/apache/lucene/analysis/sq/stopwords.txt, thrown from ModuleResourceLoader.openResource.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
MP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
MP=$MP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
MP=$MP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
MP=$MP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
MP=$MP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
MP=$MP:randomizedtesting-jupiter-0.4.0.jar:junit-jupiter-api-6.1.3.jar:junit-jupiter-params-6.1.3.jar
MP=$MP:junit-platform-engine-6.1.3.jar:junit-platform-commons-6.1.3.jar:opentest4j-1.3.0.jar

javac -d classes -cp "$MP" $(find src -name '*.java')

# the jars on the module path (as named modules), only the compiled test on the class path
java -ea -esa --module-path "$MP" --add-modules ALL-MODULE-PATH -cp classes -Dtests.seed=DEADBEEF \
     org.junit.runner.JUnitCore org.apache.lucene.analysis.tests.TestAlbanianStopwordsOpenToCore
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
