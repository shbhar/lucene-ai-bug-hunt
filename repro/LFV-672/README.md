# BengaliAnalyzer never removes 13 of its 116 default stop words (e.g. কারণ, শুধু, মধ্যে, অন্য) because its StopFilter runs after Indic/Bengali normalization while the bundled stopwords.txt has only unnormalized spellings

Bug LFV-672 · Area: analysis-common / Bengali analyzer default stop words (BengaliAnalyzer, bn/stopwords.txt; interaction with IndicNormalizationFilter and BengaliNormalizationFilter) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index Bengali text with the stock BengaliAnalyzer, whose constructor javadoc says it uses the default stop words from its bundled stopwords.txt, so a listed word such as কারণ ('because') should produce no token. But the analyzer runs IndicNormalizationFilter and BengaliNormalizationFilter before its StopFilter, and they rewrite letters (শ and ষ to স, ণ to ন, ঙ to ং, the ্য sign is dropped), while the bundled list holds only the original spellings, unlike the Hindi list in the same module, which also carries the normalized forms. So 13 of the 116 default stop words never match and are indexed and searched as ordinary terms (কারণ becomes কারন, শুধু becomes সুধু, অন্য becomes অন), with no error.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/analysis/bn/TestBengaliDefaultStopwords.java](src/org/apache/lucene/analysis/bn/TestBengaliDefaultStopwords.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.bn.TestBengaliDefaultStopwords
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
