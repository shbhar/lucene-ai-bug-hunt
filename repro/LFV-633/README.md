# ConfusionMatrixGenerator never cancels a classification that exceeded its 5 s limit, so later documents queue behind it on the single worker thread and are also counted as timed out

Bug LFV-633 · Area: classification / ConfusionMatrixGenerator (classifier evaluation utility) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You evaluate a classifier with ConfusionMatrixGenerator.getConfusionMatrix and one document's classification is slow (more than about 10 s) or hangs. You expect, as the code's own comment ('fail if classification takes more than 5s') and its skip-and-continue timeout branch intend, that only that document counts as a timeout and the other documents are still classified and counted. Instead the timed-out classification is never cancelled and keeps the generator's only worker thread, so the next documents wait behind it, their own 5 s waits expire while they are still queued, and they are dropped too: with a hung first document 0 of 2 instantly classifiable documents are counted (empty matrix, average time Infinity), with a 12 s one 1 of 2.

The test shows that `ConfusionMatrixGenerator.getConfusionMatrix` does not cancel a classification that exceeded its 5 s limit, so the next document, whose own classification is instant, waits behind it on the single worker thread and is counted as timed out too (the test takes about 10 s: two hard-coded 5 s timeouts).

## Expected vs actual

With doc 0 stuck and doc 1 instant, expected `getNumberOfEvaluatedDocs() == 1`; actual: `evaluated documents expected:<1> but was:<0>`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/classification/utils/TestConfusionMatrixTimedOutClassification.java](src/org/apache/lucene/classification/utils/TestConfusionMatrixTimedOutClassification.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/classification/build/libs/lucene-classification-$V.jar
CP=$CP:$LUCENE/lucene/grouping/build/libs/lucene-grouping-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.classification.utils.TestConfusionMatrixTimedOutClassification
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
