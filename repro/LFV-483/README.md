# DateRecognizerFilterFactory gives every filter it creates the same java.text.DateFormat, so an analyzer built from it throws NumberFormatException or ArrayIndexOutOfBoundsException when several threads analyse text at the same time

Bug LFV-483 · Area: analysis / analysis-common miscellaneous filters (DateRecognizerFilterFactory, DateRecognizerFilter) · Priority: medium · Impact: crash · Novelty: new

Bug page: [../../bugs/LFV-483.html](../../bugs/LFV-483.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an analyzer with CustomAnalyzer from a whitespace tokenizer and the dateRecognizer filter (datePattern=yyyy-MM-dd, locale=en-US) and analyse text such as '2024-01-15 foo 1999-12-31' from four threads at once, as IndexWriter does with its indexing threads. You expect every thread to get the same date tokens as a single-threaded run, because Analyzer gives each thread its own set of token stream components. Instead some analyses throw NumberFormatException ('multiple points', 'For input string: ""') or ArrayIndexOutOfBoundsException, because DateRecognizerFilterFactory creates one java.text.DateFormat, which is not thread-safe, and create passes that same instance to the filter of every thread.

The test shows that an analyzer built with `DateRecognizerFilterFactory` (via CustomAnalyzer) throws when two threads use it at once: every filter the factory creates gets the same `java.text.DateFormat`, which is not thread-safe.

## Expected vs actual

Expected no exception (Analyzer gives each thread its own filter); actual `NumberFormatException` out of `SimpleDateFormat.parse` in `DateRecognizerFilter.accept` (-ea and -da; needs two CPUs running the threads in parallel).

## Files

- [src/org/apache/lucene/analysis/miscellaneous/TestDateRecognizerFactoryThreads.java](src/org/apache/lucene/analysis/miscellaneous/TestDateRecognizerFactoryThreads.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.miscellaneous.TestDateRecognizerFactoryThreads
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
