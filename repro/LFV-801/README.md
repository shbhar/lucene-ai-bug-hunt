# KNearestFuzzyClassifier ignores its documented 'field^boost' syntax: an entry such as 'title^10' in textFieldNames is looked up as a literal field name, so that field contributes nothing, and with a single boosted field assignClass returns null

Bug LFV-801 · Area: classification / KNearestFuzzyClassifier (textFieldNames 'field^boost' syntax; NearestFuzzyQuery field lookup) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-801.html](../../bugs/LFV-801.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a KNearestFuzzyClassifier with a text field written as 'text^2' or 'title^10', as its constructor javadoc says you may, and expect the same classes as with the plain field name (with that field weighted more). Instead the entry is used verbatim as the name of a field called 'text^2', which does not exist, so that field adds no terms to the query: with one text field assignClass returns null and getClasses an empty list, and with several fields the boosted one is silently ignored. The sibling KNearestNeighborClassifier, whose javadoc has the same sentence, parses the boost and classifies normally.

## Expected vs actual

- Expected: `text^2` classifies like `text` (fruit) and `getClasses` is not empty; `{"title^10", "body"}` and `{"title^1", "body"}` give fruit, like `{"title", "body"}`.
- Actual: `text^2` gives `null` and `getClasses` returns ``; both boosted two-field calls give vehicle, the body-only answer: 4 of 4 tests fail.

## Files

- [src/org/apache/lucene/classification/TestKNearestFuzzyClassifierFieldBoost.java](src/org/apache/lucene/classification/TestKNearestFuzzyClassifierFieldBoost.java) (JUnit test)

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
     execute --select-class org.apache.lucene.classification.TestKNearestFuzzyClassifierFieldBoost
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestKNearestFuzzyClassifierBoostedFieldNames.java](TestKNearestFuzzyClassifierBoostedFieldNames.java)
- [TestKNearestFuzzyBoostProbe.java](TestKNearestFuzzyBoostProbe.java)
- [TestKNearestFuzzyBoostedFieldV1.java](TestKNearestFuzzyBoostedFieldV1.java)
- [TestKNearestFuzzyClassifierFieldBoost.java](TestKNearestFuzzyClassifierFieldBoost.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
