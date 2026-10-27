# BooleanPerceptronClassifier adds its term weights to its FST in Java String (UTF-16) order instead of the UTF-8 byte order the FST requires, so a text field with a term starting with a supplementary character (such as an emoji) and a term starting with a character in U+E000..U+FFFF (such as a fullwidth letter) makes training throw AssertionError with -ea and, with -da, silently loses term weights (in some term layouts all of them) or throws ArrayIndexOutOfBoundsException (released since 5.0.0)

Bug LFV-395 · Area: classification / BooleanPerceptronClassifier (weights FST) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-395.html](../../bugs/LFV-395.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index two documents, 'P P P' labelled true and 'N' labelled false, where P is U+F900 (a CJK compatibility ideograph) and N is U+1D400 (mathematical bold capital A), train BooleanPerceptronClassifier with the default bias (the average term frequency, here 2), and expect assignClass(P) = (true, 0.393), which is what the same corpus gives with ASCII terms or with ordinary CJK ideographs. Instead the constructor throws AssertionError 'inputs are added out of order lastInput=[f0 9d 90 80] vs input=[ef a4 80]' with -ea, and with -da assignClass(P) returns (false, 0.632) because P's weight can no longer be found in the FST. The classifier keeps its weights in a ConcurrentSkipListMap&lt;String, Double&gt;, whose String order (UTF-16 code units) puts U+1D400 (D835 DC00) before U+F900, and adds their UTF-8 encodings in that order to an FSTCompiler that requires UTF-8 byte order (EF before F0) and checks it only with an assert.

## Expected vs actual

- Expected: `assignClass("ｓａｌｅ")` = (true, 0.393) and `assignClass("🎉")` = (false, 0.393), as with the ASCII and katakana controls.
- Actual: Assertions off: `assignClass("ｓａｌｅ")` = (false, 0.632); assertions on: `AssertionError: inputs are added out of order` from the constructor.

## Files

- [src/org/apache/lucene/classification/TestBooleanPerceptronUtf8Order.java](src/org/apache/lucene/classification/TestBooleanPerceptronUtf8Order.java) (JUnit test)

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
     execute --select-class org.apache.lucene.classification.TestBooleanPerceptronUtf8Order
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestBooleanPerceptronTermOrder.java](TestBooleanPerceptronTermOrder.java)
- [TestPerceptronUtf8InputOrderV1.java](TestPerceptronUtf8InputOrderV1.java)
- [TestPerceptronUtf8Order.java](TestPerceptronUtf8Order.java)
- [TestBooleanPerceptronUtf8Order.java](TestBooleanPerceptronUtf8Order.java)
- [TestPerceptronFstOrderSecurityReach.java](TestPerceptronFstOrderSecurityReach.java)

Formal models and scripts this bug's part of the hunt used: [../../models/classification/](../../models/classification/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
