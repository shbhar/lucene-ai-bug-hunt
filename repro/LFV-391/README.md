# SimpleNaiveBayesClassifier applies its documented training query only to the per-word counts: the class prior and the likelihood denominators are computed over the whole index, so labelled documents outside the training set can decide the class (released since 4.6.0; same pattern in SimpleNaiveBayesDocumentClassifier and CachingNaiveBayesClassifier)

Bug LFV-391 · Area: classification / SimpleNaiveBayesClassifier (training query; copies in SimpleNaiveBayesDocumentClassifier and CachingNaiveBayesClassifier) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index four training documents marked split:train (fruit: 'apple', 'pear', 'plum'; vehicle: 'car') and ten more vehicle documents marked split:other, build SimpleNaiveBayesClassifier with the training query split:train and classify 'apple', expecting what the same classifier trained on an index holding only the four training documents answers: fruit (0.811), with prior P(fruit) = 3/4. Instead it answers vehicle (P(fruit) = 0.445), and for an empty input, which only the prior decides, it gives P(fruit) = 3/14, the whole-index share. The query is ANDed only into the per-word counts (getWordFreqForClass); the prior (docFreq(class) / Terms.getDocCount) and the likelihood denominator (average unique terms per document x docFreq(class) + that document count) ignore it, because countDocsWithClass applies the query only in a getDocCount == -1 fallback that current codecs never take.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: It answers vehicle (P(fruit) = 0.445), and for an empty input, which only the prior decides, it gives P(fruit) = 3/14, the whole-index share.

## Files

- [src/org/apache/lucene/classification/TestSimpleNaiveBayesTrainingQueryPrior.java](src/org/apache/lucene/classification/TestSimpleNaiveBayesTrainingQueryPrior.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.classification.TestSimpleNaiveBayesTrainingQueryPrior
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
