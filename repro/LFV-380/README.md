# SimpleNaiveBayesDocumentClassifier returns NaN for every class and null from assignClass when the document has a field value with no tokens (empty, stop words only or punctuation only), because each value's log-likelihood is divided by its token count (released since 6.0.0)

Bug LFV-380 · Area: classification / document classifiers (SimpleNaiveBayesDocumentClassifier.assignClass and getClasses) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-380.html](../../bugs/LFV-380.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You train SimpleNaiveBayesDocumentClassifier on the fields title and body and classify the document {title: 'apple', body: ''} (or a body such as 'the of and' that the analyzer reduces to stop words), expecting a class with a finite score, as the DocumentClassifier.assignClass javadoc and the '0-1 range' normalisation comment imply and as the same document without the body value gets (fruit). Instead getClasses returns fruit=NaN and vehicle=NaN, and assignClass returns null. calculateLogLikelihood returns result / tokenizedText.length, which is 0.0 / 0 = NaN for a value with no tokens; assignNormClasses adds that NaN to every class, and neither the log-sum-exp normalisation nor the 'score &gt; maxscore' test in assignClass can recover from it.

## Expected vs actual

- Expected: `getClasses` returns finite probabilities that sum to 1 and `assignClass` a class; without the body value: fruit 0.7619, vehicle 0.2381.
- Actual: `getClasses=[fruit=NaN vehicle=NaN ] assignClass=null` for body `""`, `"To be or not to be"` and `"!!!"`; no exception.

## Files

- [src/org/apache/lucene/classification/document/TestSimpleNaiveBayesDocumentClassifierEmptyField.java](src/org/apache/lucene/classification/document/TestSimpleNaiveBayesDocumentClassifierEmptyField.java) (JUnit test)

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
     execute --select-class org.apache.lucene.classification.document.TestSimpleNaiveBayesDocumentClassifierEmptyField
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestSimpleNaiveBayesDocumentClassifierZeroTokenValue.java](TestSimpleNaiveBayesDocumentClassifierZeroTokenValue.java)
- [TestSimpleNaiveBayesDocumentClassifierEmptyField.java](TestSimpleNaiveBayesDocumentClassifierEmptyField.java)
- [TestNaiveBayesDocZeroTokenValue.java](TestNaiveBayesDocZeroTokenValue.java)
- [TestZeroTokenValueProbe.java](TestZeroTokenValueProbe.java)

Formal models and scripts this bug's part of the hunt used: [../../models/classification/](../../models/classification/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
