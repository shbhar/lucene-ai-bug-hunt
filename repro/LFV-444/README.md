# KoreanTokenizer with outputUnknownUnigrams=true emits every punctuation, symbol and whitespace character inside an unknown word as its own token ('$', '@', '.', ' ', '\t', '\n') although discardPunctuation=true (the default): LUCENE-8784 (8.2.0) dropped the punctuation check from the unigram branch

Bug LFV-444 · Area: analysis / nori KoreanTokenizer (Viterbi.backtrace unigram branch; KoreanTokenizerFactory, KoreanAnalyzer) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-444.html](../../bugs/LFV-444.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyze '가격: $100' or '다음\n\n문장' with KoreanTokenizer(factory, null, DISCARD, outputUnknownUnigrams=true, discardPunctuation=true), or with the 'korean' factory given only outputUnknownUnigrams="true", and expect punctuation and whitespace to be dropped, as the discardPunctuation javadoc says ('true if punctuation tokens should be dropped from the output') and as happens with outputUnknownUnigrams=false ([가격, 100], [다음, 문장]) and in 7.4.0-8.1.1. Instead you get [가격, $, 1, 0, 0] and [다음, \n, \n, 문장], and 'test@example.com' keeps its '@' and '.': the unigram branch of nori's Viterbi.backtrace adds each single-character token of an unknown word without the shouldFilterToken check that the other branch applies. Every such character becomes an index term and pushes the following words one position further, so an exact phrase across it no longer matches.

## Expected vs actual

- Expected: `가격: $100` analyzes to `[가격, 1, 0, 0]`, the line breaks, `%`, spaces, tab, `@` and `.` are dropped, and the phrase `가격 100` finds 1 document.
- Actual: `가격: $100` analyzes to `[가격, $, 1, 0, 0]`, `다음\n\n문장` keeps two `\n` tokens, the e-mail address keeps `@`, and the phrase `가격 100` finds 0 documents.

## Files

- [src/org/apache/lucene/analysis/ko/TestKoreanUnigramsDiscardPunctuation.java](src/org/apache/lucene/analysis/ko/TestKoreanUnigramsDiscardPunctuation.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/nori/build/libs/lucene-analysis-nori-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ko.TestKoreanUnigramsDiscardPunctuation
```

The module(s) analysis-nori also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestKoreanTokenizerUnigramPunctuation.java](TestKoreanTokenizerUnigramPunctuation.java)
- [TestKoreanUnigramsDiscardPunctuation.java](TestKoreanUnigramsDiscardPunctuation.java)
- [TestNoriUnigramsKeepPunctuation.java](TestNoriUnigramsKeepPunctuation.java)
- [KoreanUnigramPunctHistoryProbe.java](KoreanUnigramPunctHistoryProbe.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
