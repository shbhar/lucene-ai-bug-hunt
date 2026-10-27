# ConfusionMatrix.getPrecision leaves out classes that are assigned but never the correct answer, so their false positives do not lower macro precision (1.0 instead of 0.5 when 3 of 4 'sports' documents are answered 'politics')

Bug LFV-768 · Area: classification / ConfusionMatrixGenerator.ConfusionMatrix (classifier evaluation utility: macro-averaged precision) · Priority: medium · Impact: minor · Novelty: new

Bug page: [../../bugs/LFV-768.html](../../bugs/LFV-768.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You evaluate a classifier with ConfusionMatrixGenerator.getConfusionMatrix and read ConfusionMatrix.getPrecision, documented as 'the macro averaged precision (see getPrecision(String)) over all the classes', and expect the mean of getPrecision(String) over every class in the matrix, including a class the classifier assigned but that no evaluated document has as its correct answer (its per-class precision is correctly 0). Instead getPrecision averages only over the matrix's row keys, which are the correct answers, so such an assigned-only class is left out of both the sum and the count: four 'sports' documents of which three are answered 'politics' give macro precision 1.0 instead of 0.5, and getF1Measure, which is built from it, is overstated too.

## Expected vs actual

- Expected: `getPrecision` = 0.5 in both failing tests, the mean of 1.0 (sports) and 0.0 (politics).
- Actual: `getPrecision` = 1.0 in both, with the per-class values 1.0 and 0.0 as expected; the control gives the correct 0.25.

## Files

- [src/org/apache/lucene/classification/utils/TestConfusionMatrixMacroPrecisionClasses.java](src/org/apache/lucene/classification/utils/TestConfusionMatrixMacroPrecisionClasses.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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
     execute --select-class org.apache.lucene.classification.utils.TestConfusionMatrixMacroPrecisionClasses
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestConfusionMatrixMacroPrecision.java](TestConfusionMatrixMacroPrecision.java)
- [TestConfusionMatrixMacroPrecisionClasses.java](TestConfusionMatrixMacroPrecisionClasses.java)
- [TestConfusionMatrixMacroPrecisionV1.java](TestConfusionMatrixMacroPrecisionV1.java)
- [TestConfusionMatrixMetricsProbe.java](TestConfusionMatrixMetricsProbe.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
