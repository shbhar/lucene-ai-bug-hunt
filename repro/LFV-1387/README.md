# HindiAnalyzer runs HindiNormalizationFilter before HindiStemFilter, so the 20 of HindiStemmer's 67 suffix rules that are spelled with a long vowel or candrabindu (-ती, -ाती, -ेगी, -ोगी, -ूंगा, -ाई, -नी, ...) can never fire, and feminine and future verb forms keep part of their suffix: 'खाती' is indexed as 'खात' while 'खाता' and 'खाते' give 'खा'

Finding LFV-1387 · Area: analysis-common / Hindi analyzer (HindiAnalyzer filter chain; HindiStemmer suffix table versus HindiNormalizer) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index Hindi text with the stock HindiAnalyzer; one document says 'वह रोज़ खाती है' ('she eats every day') and a query says 'खाता' (the masculine form of 'eats'). You expect both forms to stem to 'खा', because HindiStemmer implements Ramanathan and Rao's light stemmer, whose suffix list contains -ती, and Lucene's own TestHindiStemmer pins खाती -&gt; खा next to खाता -&gt; खा. Instead HindiAnalyzer runs HindiNormalizationFilter first, which folds the long vowel signs ी and ू, the long vowels आ, ई and ऊ and the candrabindu ँ to their short or plain forms, so the 20 suffix rules spelled with those characters can never match: 'खाती' is indexed as 'खात' and the query finds nothing, with no error.

## Expected vs actual

- Expected: खाती (*khātī*) is analyzed to खा (*khā*), as `HindiStemFilter` alone and `TestHindiStemmer` give.
- Actual: `term 0 expected:<खा[]> but was:<खा[त]>`: the analyzer gives खात (*khāt*), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/hi/TestHindiAnalyzerFeminineVerb.java](src/org/apache/lucene/analysis/hi/TestHindiAnalyzerFeminineVerb.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.hi.TestHindiAnalyzerFeminineVerb
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this finding's part of the hunt used: [../../models/hindi-analyzer-long-vowel-fold-before-hindistemmer-loses-feminine-and-future-suffix-rules/](../../models/hindi-analyzer-long-vowel-fold-before-hindistemmer-loses-feminine-and-future-suffix-rules/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
