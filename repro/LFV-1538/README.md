# BooleanPerceptronClassifier's constructor (in the 5.x releases its train() method) can throw IOException 'term vectors must be stored for field &lt;text field&gt;' although the field stores term vectors: a training document whose text has no token has no term vector, and when that document is misclassified at its turn the class reads this as a missing field configuration (run: an empty text labelled true under StandardAnalyzer with the automatic bias; the same document labelled false trains; an experimental class; 5.0.0 to 10.6.0, branch_11x and main)

Finding LFV-1538 · Area: classification / BooleanPerceptronClassifier (training loop of the constructor: updateWeights) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You train a BooleanPerceptronClassifier with the automatic bias on an index whose text field is stored with term vectors, as the class needs, and one training document labelled true has an empty text (also run: only punctuation under StandardAnalyzer, only a stop word under EnglishAnalyzer). You expect a trained classifier, since the same document labelled false is accepted and nothing in the class's documentation requires a token in every training document, or at least an error that names that document. Instead the constructor throws IOException 'term vectors must be stored for field text': Lucene stores no term vector for a document whose field has no token, and when the training loop finds that document misclassified, the classifier takes its missing term vector for a field that stores none.

## Expected vs actual

- Expected: The constructor returns a classifier (or fails with an error that names document 1; no javadoc says which).
- Actual: `java.io.IOException: term vectors must be stored for field text` at `BooleanPerceptronClassifier.updateWeights(BooleanPerceptronClassifier.java:184)`, although the field stores term vectors.

## Files

- [src/org/apache/lucene/classification/TestBooleanPerceptronClassifierEmptyText.java](src/org/apache/lucene/classification/TestBooleanPerceptronClassifierEmptyText.java) (JUnit test)

## Run it

- Tested on Lucene main 1ae70c6e45b8 (a commit of 2026-11-19): still reproduces.
- Tested on Lucene branch_11x 89e68e0f3a50 (a commit of 2026-11-17): reproduced.
- A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
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
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.classification.TestBooleanPerceptronClassifierEmptyText
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
