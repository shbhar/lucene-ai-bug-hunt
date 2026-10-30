# The benchmark quality package overview's TREC sample calls qrun.execute, a 4-argument QualityBenchmark.execute that no release has ever had (maxResults moved to setMaxResults(int) in LUCENE-1116, before 2.3.0, the package's first release), so the sample has not compiled in any release (documentation defect)

Bug LFV-1040 · Area: benchmark / search-quality package (quality/package-info.java: the package overview's TREC sample) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You start a search-quality evaluation from the benchmark quality package overview, which presents its code block as 'a sample code used to run the TREC 2006 queries 701-850 on the.Gov2 collection', and expect it to compile against the package's API. It does not, on main or on any release since 2.3.0: the sample calls qrun.execute, but QualityBenchmark has only execute and takes the result count from setMaxResults(int), so javac rejects the call (the DirectoryReader.open line also ends with ':' instead of ';'). The API change was deliberate (LUCENE-1116, 2008) and the in-tree QueryDriver does it right (setMaxResults, then the 3-argument execute); only the page was never updated.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/benchmark/quality/TestQualityPackageSample.java](src/org/apache/lucene/benchmark/quality/TestQualityPackageSample.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/benchmark/build/libs/lucene-benchmark-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.benchmark.quality.TestQualityPackageSample
```

The module(s) benchmark also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/analysis-benchmark-backward-codecs-javadoc-examples-compile-on-main/](../../models/analysis-benchmark-backward-codecs-javadoc-examples-compile-on-main/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
