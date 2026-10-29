# CachingNaiveBayesClassifier and SimpleNaiveBayesDocumentClassifier offer and assign the empty class term '', which SimpleNaiveBayesClassifier and BM25NBClassifier skip, so the Naive Bayes classifiers disagree on the same index

Bug LFV-763 · Area: classification / Naive Bayes classifiers (CachingNaiveBayesClassifier, SimpleNaiveBayesDocumentClassifier vs SimpleNaiveBayesClassifier, BM25NBClassifier) · Priority: low · Impact: minor · Novelty: new

Bug page: [../../bugs/LFV-763.html](../../bugs/LFV-763.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index training documents where some carry an empty class value (for example StringField("cat", "") for unlabelled rows) and build SimpleNaiveBayesClassifier, CachingNaiveBayesClassifier and SimpleNaiveBayesDocumentClassifier on the same reader, expecting the two subclasses to offer the same classes as their parent, which (like BM25NBClassifier) skips the empty class term with 'if (next.length &gt; 0)'. Instead the caching and document classifiers list '' as a class: getClasses('car') gives ['', 'fruit', 'vehicle'] against the parent's ['fruit', 'vehicle'], and with two empty-class 'car bus' documents both assign '' to 'car bus' where the parent assigns 'vehicle'. Their class loops (CachingNaiveBayesClassifier.reInitCache and SimpleNaiveBayesDocumentClassifier.assignNormClasses) lack the parent's length check; whether the fix adds it there or removes it from the parent and BM25NBClassifier is open.

The test shows that on an index where one training document has an empty class value (`StringField("cat", "")`), `CachingNaiveBayesClassifier.getClasses` offers the empty class `''`, which its parent `SimpleNaiveBayesClassifier` skips.

## Expected vs actual

Expected: the parent's classes `['fruit', 'vehicle']`. Actual: `['', 'fruit', 'vehicle']`.

## Files

- [src/org/apache/lucene/classification/TestCachingNaiveBayesEmptyClassValue.java](src/org/apache/lucene/classification/TestCachingNaiveBayesEmptyClassValue.java) (JUnit test)

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
     execute --select-class org.apache.lucene.classification.TestCachingNaiveBayesEmptyClassValue
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
