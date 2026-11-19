# When Lucene runs as named modules, CustomAnalyzer.builder() and builder(Path) cannot load a stop-word file that ships in a Lucene analysis jar, and the remedy that the javadoc and the exception message name (open the package to org.apache.lucene.core) has no effect for the class-loader lookup they use; every release from 9.1.0 to 10.6.0, the 11.0 branch and main; known upstream (#11384, #567)

Finding LFV-1513 · Area: analysis / CustomAnalyzer default builders and ClasspathResourceLoader (resource lookup on the module path) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run Lucene as named modules (the jars on the module path) and build an analyzer with CustomAnalyzer.builder(), naming the stop-word file org/apache/lucene/analysis/snowball/german_stop.txt that ships in lucene-analysis-common. You expect the token hund for 'der hund', as on the class path: the builder's javadoc says it loads all resources from Lucene's class loader, and that package is opened to org.apache.lucene.core so that the resource loaders can see it (LUCENE-10348). Instead the builder throws IOException 'Resource not found (if you use Java Module System, make sure to open module and package containing resources to 'org.apache.lucene.core' module)': the two builders that take no loader look resources up through a class loader, which returns a named module's resource only from a package opened to all modules, so the remedy that the message names has no effect for them, while builder(new ModuleResourceLoader(...)) works. Known upstream, not a new bug: apache/lucene#11384 (LUCENE-10348) describes the symptom and apache/lucene#567 states the mechanism, both by Uwe Schindler. What no upstream item found says: that these two builders still fail after the fix of #11384 (#581, in 9.1.0), that the remedy text has no effect for them, and which releases are affected.

## Expected vs actual

- Expected: You expect the token hund for 'der hund', as on the class path: the builder's javadoc says it loads all resources from Lucene's class loader, and that package is opened to org.apache.lucene.core so that the resource loaders can see it (LUCENE-10348).
- Actual: The builder throws IOException 'Resource not found (if you use Java Module System, make sure to open module and package containing resources to 'org.apache.lucene.core' module)': the two builders that take no loader look resources up through a class loader, which returns a named module's resource only from a package opened to all modules, so the remedy that the message names has no effect for them, while builder(new ModuleResourceLoader(...)) works.

## Files

- [src/org/apache/lucene/analysis/tests/TestCustomAnalyzerDefaultBuilderStopwords.java](src/org/apache/lucene/analysis/tests/TestCustomAnalyzerDefaultBuilderStopwords.java) (JUnit test)

## Run it

- Tested on Lucene main 9965d34e3c77 (a commit of 2026-11-16): reproduced with Lucene's jars on the module path (as named modules), with assertions on and off; on the class path the same test passes.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced with Lucene's jars on the module path (as named modules), with assertions on and off; on the class path the same test passes.
- Tested on Lucene branch_11x daa54f2e0cd3 (a commit of 2026-11-13): reproduced on the module path, with assertions on and off; passes on the class path.
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced on the module path, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0); passes on the class path.
- This test fails only when Lucene's jars run as named modules, that is on the module path. The commands below compile the test and then start the compiled class with the jars on the module path and only the classes directory on the class path, with the java of JDK 25 (the recorded runs used 25.0.3); for a run without assertions put -da -dsa -Dtests.asserts=false in place of -ea -esa. With the same jars on the class path the test passes. The six jars after hamcrest-3.0.jar are on Maven Central too; the module of the test framework of 11.0 and of main cannot be resolved without them (the JVM then stops with a FindException). --add-modules ALL-MODULE-PATH is needed because the test class itself is on the class path. No Lucene jar may be on the class path as well: with the jars on both paths the lookup succeeds and the test passes. The same MP and java lines fail the same way with the four 10.6.0 release jars from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework, lucene-analysis-common) in place of the four $LUCENE jars; on a branch_11x checkout set V=11.0.0-SNAPSHOT. What the test does: it builds an analyzer with CustomAnalyzer.builder(), the whitespace tokenizer and a stop filter whose word file is org/apache/lucene/analysis/snowball/german_stop.txt, a file inside lucene-analysis-common, and expects the one token hund for 'der hund'. The outcome does not depend on the seed. For the Gradle alternative below the matching module is lucene/analysis.tests, whose tests Lucene's build runs as a named module: copy the test to lucene/analysis.tests/src/test/org/apache/lucene/analysis/tests/ and run ./gradlew -p lucene/analysis.tests test --tests TestCustomAnalyzerDefaultBuilderStopwords -Ptests.seed=DEADBEEF -Ptests.asserts=true; it fails 1 of 1 with the same exception on main 781025d6fe32 and on branch_11x daa54f2e0cd3, and it does the same with -Ptests.asserts=false and with neither flag (Lucene's build then takes the assertion setting from the seed, and for DEADBEEF it runs without assertions).
- This finding was re-run on Lucene main 1ae70c6e45b8 on 2026-11-19: still reproduces.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test run and 1 failure, with java.io.IOException: Resource not found (if you use Java Module System, make sure to open module and package containing resources to 'org.apache.lucene.core' module): org/apache/lucene/analysis/snowball/german_stop.txt, thrown from the builder's addTokenFilter call.
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
     org.junit.runner.JUnitCore org.apache.lucene.analysis.tests.TestCustomAnalyzerDefaultBuilderStopwords
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
