# BM25NBClassifier scores a class that has no live, training-query-matching document as if it had probability 1 (log prior 0 and log-likelihood 0), so with few classes it outranks the trained classes and is assigned although it has no training data (released since 7.0.0)

Bug LFV-388 · Area: classification / BM25NBClassifier (assignClass and getClasses) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-388.html](../../bugs/LFV-388.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index four training documents labelled fruit ('apple banana', 'apple pear') or vehicle ('car truck', 'car bus') plus one document labelled heldout that your training query split:train excludes, build BM25NBClassifier with that query and classify 'apple', expecting fruit, which is what the classifier answers when the heldout document is absent. Instead assignClass returns heldout (getClasses: heldout 0.568, fruit 0.273, vehicle 0.159), and the same happens with no training query when every document of a label is deleted but its segment is not yet merged ('apple' -&gt; gone 0.681). BM25NBClassifier makes every term of the class field a candidate class (MultiTerms.getTerms), and for a class with no live, query-matching document both of its searches find no hit, so calculateLogPrior falls back to 0 and getTermProbForClass to 1 (log 0): the class scores exactly 0, while each trained class sums log(top BM25 score) over the prior and every input token, which is negative whenever those scores are below 1.

The test shows that BM25NBClassifier built with the training query split:train assigns a class whose only document that query excludes (`TestBM25NBUntrainedClass#testClassExcludedByTrainingQueryIsNotAssigned`, 3 documents).

## Expected vs actual

Expected: assignClass("apple") returns "fruit". Actual: "heldout" (its zero-hit fallbacks give it log prior 0 and log-likelihood 0, the score of a probability-1 class); fails with -ea and -da.

## Files

- [src/org/apache/lucene/classification/TestBM25NBUntrainedClass.java](src/org/apache/lucene/classification/TestBM25NBUntrainedClass.java) (JUnit test)

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
     execute --select-class org.apache.lucene.classification.TestBM25NBUntrainedClass
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/classification/](../../models/classification/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
