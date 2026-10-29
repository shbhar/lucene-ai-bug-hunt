# KNearestNeighborClassifier, KNearestFuzzyClassifier, KNearestNeighborDocumentClassifier and BooleanPerceptronClassifier add their documented training filter query (and the class-field restriction) as scoring Occur.MUST clauses, so a training query that filters out nothing changes the k-NN class scores and can flip the assigned class, the nearest neighbours or the learned perceptron model (released since 4.6.0)

Bug LFV-684 · Area: classification / k-NN classifiers and BooleanPerceptronClassifier (training query) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-684.html](../../bugs/LFV-684.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index three labelled documents ('aa bb' as Y, 'aa' as X, 'aa' as X), build KNearestNeighborClassifier with k = 3 once with no training query and once with MatchAllDocsQuery (the parameter is documented as 'a Query to eventually filter the docs used for training the classifier', and this one removes nothing), and expect the same answer for 'aa bb bb'. Instead the answer flips from Y (class scores Y 0.333, X 0.309) to X (X 0.417, Y 0.333), because the training query and the class-field WildcardQuery are added as scoring Occur.MUST clauses, so their scores are added to every neighbour's MoreLikeThis score before each vote is weighted by score / maxScore; a training query whose score differs between documents, such as TermQuery(split:train) on a text field every document has, even replaces the nearest neighbour (k = 1: 'aa', class X, becomes 'aa bb', class Y). KNearestFuzzyClassifier and KNearestNeighborDocumentClassifier return different class scores in the same way, and BooleanPerceptronClassifier, which trains in the score order of the same kind of query, learns a different model ('aa' answers true instead of false).

The test shows that KNearestNeighborClassifier scores its training query (added as Occur.MUST), so MatchAllDocsQuery, a training query that filters out nothing, changes the assigned class (3 docs, k = 3).

## Expected vs actual

Expected the null-training-query class `Y`; actual `X` (`expected:<[Y]> but was:<[X]>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/classification/TestKnnTrainingQueryIsScored.java](src/org/apache/lucene/classification/TestKnnTrainingQueryIsScored.java) (JUnit test)

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
     execute --select-class org.apache.lucene.classification.TestKnnTrainingQueryIsScored
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
