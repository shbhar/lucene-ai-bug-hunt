# SoraniNormalizer tests for format characters one UTF-16 char at a time, so format characters outside the BMP (U+E0001, the tag characters U+E0020..U+E007F, U+1BCA0..U+1BCA3, U+1D173..U+1D17A and others) are kept in the term, while BMP ones such as RLM and LRM are removed

Bug LFV-752 · Area: analysis-common / Sorani normalizer (SoraniNormalizer.normalize; SoraniNormalizationFilter, SoraniAnalyzer and the 'soraniNormalization' factory) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-752.html](../../bugs/LFV-752.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyze Sorani Kurdish text with the stock SoraniAnalyzer or SoraniNormalizationFilter. The normalizer's javadoc says formatting characters are removed, and it does remove BMP ones such as the right-to-left mark, so کورد + X + ستان should become کوردستان for any invisible format character X. Instead it calls Character.getType on one UTF-16 char at a time, so a format character outside the BMP (for example U+E0001 or a tag character), which Java stores as two surrogate chars, is never recognized: it stays in the term, and the word no longer matches the same word without it, with no error.

## Expected vs actual

- Expected: Every format character is removed: کورد + U+E0001 + ستان becomes کوردستان, and the plain word finds all 3 documents.
- Actual: 3 of 4 tests fail: U+E0001, U+E0067, U+1BCA0 and U+1D173 stay in the term, `normalize` keeps U+E0001, and the plain word finds 1 of 3 documents.

## Files

- [src/org/apache/lucene/analysis/ckb/TestSoraniSupplementaryFormatChars.java](src/org/apache/lucene/analysis/ckb/TestSoraniSupplementaryFormatChars.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ckb.TestSoraniSupplementaryFormatChars
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestSoraniSupplementaryFormatRemoval.java](TestSoraniSupplementaryFormatRemoval.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
