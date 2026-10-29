# TeluguAnalyzer runs its StopFilter after Indic and Telugu normalization, but its default stop list is not normalized, so 13 of the 43 single-word default stop words (for example కూడా, 'also') are never removed

Bug LFV-677 · Area: analysis-common / language analyzers (TeluguAnalyzer default stop set te/stopwords.txt, applied after IndicNormalizationFilter and TeluguNormalizationFilter) · Priority: low · Impact: minor · Novelty: new

Bug page: [../../bugs/LFV-677.html](../../bugs/LFV-677.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyze Telugu text with the stock new TeluguAnalyzer, whose default stop list (te/stopwords.txt) holds common function words such as కూడా ('also') that the analyzer should drop. The analyzer normalizes every token before its StopFilter and folds long vowel signs to short ones (ూ to ు, ీ to ి, ే to ె, ో to ొ), but the list holds only the unnormalized spellings, so the StopFilter sees కుడా and never matches the entry కూడా. As a result 13 of the 43 single-word default stop words are never removed and are indexed and searched as ordinary terms, while the Hindi analyzer, which has the same chain, ships the normalized forms in its list and removes all of its stop words.

The test shows that the stock `new TeluguAnalyzer()` does not remove its own default stop word కూడా ('also'), because its StopFilter runs after normalization and the list is not normalized.

## Expected vs actual

Expected no token; actual one token: `TokenStream has more tokens than expected (expected count=0); extra token=కుడ` (-ea and -da).

## Files

- [src/org/apache/lucene/analysis/te/TestTeluguDefaultStopword.java](src/org/apache/lucene/analysis/te/TestTeluguDefaultStopword.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.te.TestTeluguDefaultStopword
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
