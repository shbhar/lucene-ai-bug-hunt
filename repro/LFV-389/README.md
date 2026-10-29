# Test suite on main: #16028 removed @Test from six test classes that do not extend LuceneTestCase, so their 18 test methods never run (TestRuntimeDependenciesSane and TestCandidateMatcherVisibility report no tests; TestClassloadingDeadlock, TestModularLayer and TestScripts each report one green placeholder; the opt-in TestHunspellRepositoryTestCases fails with 'No runnable methods')

Bug LFV-389 · Area: test infrastructure / JUnit 4 test discovery (core, core.tests, monitor, distribution.tests, analysis-common hunspell) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run Lucene's test suite on main and expect TestRuntimeDependenciesSane, TestCandidateMatcherVisibility, TestClassloadingDeadlock, TestModularLayer and TestScripts to run their 17 test methods, and TestHunspellRepositoryTestCases to check Hunspell's test data when -Phunspell.repo.path is set. Instead the build passes without running any of those methods: #16028 removed their @Test annotations, which is harmless in LuceneTestCase subclasses (LuceneTestCase finds test* methods by name), but these six classes do not extend LuceneTestCase, so JUnit 4 finds only @Test methods and there are none. Two classes report zero tests, three report one green placeholder named after the class, and the Hunspell runner fails with 'No runnable methods'.

The test shows that main's TestCandidateMatcherVisibility (not a LuceneTestCase subclass) lost its @Test annotations in #16028, so JUnit 4 finds none of its three test* methods.

## Expected vs actual

Expected `JUnitCore.runClasses(TestCandidateMatcherVisibility.class)` to run 3 tests with no failure; actual: run=1, `initializationError... No runnable methods`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/monitor/outsidepackage/TestCandidateMatcherVisibilityRuns.java](src/org/apache/lucene/monitor/outsidepackage/TestCandidateMatcherVisibilityRuns.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/monitor/build/libs/lucene-monitor-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.monitor.outsidepackage.TestCandidateMatcherVisibilityRuns
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
