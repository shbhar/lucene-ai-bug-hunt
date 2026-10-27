# SimpleNaiveBayesDocumentClassifier applies a documented 'field^boost' (e.g. 'title^10') only on its first call: it writes the stripped field name back into the caller's textFieldNames array, so every later classification silently returns the unboosted result (released since 6.0.0)

Bug LFV-382 · Area: classification / document classifiers (SimpleNaiveBayesDocumentClassifier field boosts) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-382.html](../../bugs/LFV-382.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a SimpleNaiveBayesDocumentClassifier with textFieldNames {"title^10", "body"}, as its constructor javadoc allows, and classify documents with it one after another, expecting the title boost on every call. The first call applies the boost but also overwrites "title^10" with "title" in textFieldNames, which is the caller's own array kept by reference, so from the second call on every boost is 1: classifying the same document twice gives fruit (0.99994) and then vehicle (0.55373), exactly the unboosted classifier's answer, and on the upstream test's own fixture ('title^100') the class flips from batman to the video-game class. No error is raised, and a second classifier built from the same String is unboosted even on its first call.

## Expected vs actual

- Expected: Both calls return `[fruit=0.99994, vehicle=6.3E-5]`, and a second classifier built from the same array also says fruit.
- Actual: Call 2 returns `[vehicle=0.55373, fruit=0.44627]`, the unboosted answer, and the second classifier says vehicle; no error.

## Files

- [src/org/apache/lucene/classification/document/TestSimpleNaiveBayesDocumentClassifierBoostReuse.java](src/org/apache/lucene/classification/document/TestSimpleNaiveBayesDocumentClassifierBoostReuse.java) (JUnit test)
- [src2/org/apache/lucene/classification/document/TestSimpleNaiveBayesDocumentClassifierBoostReuse.java](src2/org/apache/lucene/classification/document/TestSimpleNaiveBayesDocumentClassifierBoostReuse.java) (JUnit test)

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

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.classification.document.TestSimpleNaiveBayesDocumentClassifierBoostReuse
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.classification.document.TestSimpleNaiveBayesDocumentClassifierBoostReuse
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestSimpleNaiveBayesDocumentClassifierRepeatedBoost.java](TestSimpleNaiveBayesDocumentClassifierRepeatedBoost.java)
- [TestSimpleNaiveBayesDocumentClassifierBoostReuse.java](TestSimpleNaiveBayesDocumentClassifierBoostReuse.java)
- [TestSnbDocClassifierFieldBoostRepeat.java](TestSnbDocClassifierFieldBoostRepeat.java)
- [TestSnbDocBoostV2.java](TestSnbDocBoostV2.java)

Formal models and scripts this bug's part of the hunt used: [../../models/classification/](../../models/classification/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
