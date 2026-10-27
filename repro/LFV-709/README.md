# smartcn: the double-hashing step of BigramDictionary (AbstractDictionary.hash2, an operator-precedence slip) is 0 for some Chinese word + ASCII or Latin-1 punctuation pairs, so each such bigram lookup re-reads one slot 402,136 times (about 0.7 ms instead of about 0.1 us) and SmartChineseAnalyzer analyzes ordinary prose 2-7x slower and text dense in such pairs 67x-174x slower, with unchanged tokens

Bug LFV-709 · Area: analysis-smartcn / HHMM segmenter bigram dictionary (BigramDictionary.getBigramItemIndex, AbstractDictionary.hash2; reached through SmartChineseAnalyzer and HMMChineseTokenizer) · Priority: medium · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyze Chinese text written with ASCII punctuation, for example '我们要发展经济和文化,加强法律,增加资金,继续努力,共同进步.', with the default SmartChineseAnalyzer; its bigram dictionary is an open-addressing hash table with double hashing, so you expect each bigram frequency lookup to take a handful of probes and the text to cost about as much as the same sentence with full-width punctuation, which gives the same tokens. Instead AbstractDictionary.hash2 computes ((33h + d) &amp; 0xFF) and then ((33h + d) &gt;&gt; 8), because '+' binds tighter than '&amp;' and '&gt;&gt;' (AbstractDictionary.java:195-196), so the probe step is always in [0, 288] and is 0 for some keys whose last char is below U+0100, such as '对@,' or '文化@,'; when such a key's home slot holds another bigram, BigramDictionary.getBigramItemIndex (BigramDictionary.java:213-233) re-reads that same slot until i reaches PRIME_BIGRAM_LENGTH = 402,137 and then returns 'not found'. The tokens stay correct, but that sentence repeated 100 times takes about 220 ms of CPU instead of about 2.6 ms (85x on main), and 1x with a one-line guard.

## Expected vs actual

- Expected: You expect each bigram frequency lookup to take a handful of probes and the text to cost about as much as the same sentence with full-width punctuation, which gives the same tokens.
- Actual: AbstractDictionary.hash2 computes ((33h + d) &amp; 0xFF) and then ((33h + d) &gt;&gt; 8), because '+' binds tighter than '&amp;' and '&gt;&gt;' (AbstractDictionary.java:195-196), so the probe step is always in [0, 288] and is 0 for some keys whose last char is below U+0100, such as '对@,' or '文化@,'; when such a key's home slot holds another bigram, BigramDictionary.getBigramItemIndex (BigramDictionary.java:213-233) re-reads that same slot until i reaches PRIME_BIGRAM_LENGTH = 402,137 and then returns 'not found'.

## Files

- [src/org/apache/lucene/analysis/cn/smart/TestBigramLookupZeroProbeStep.java](src/org/apache/lucene/analysis/cn/smart/TestBigramLookupZeroProbeStep.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/smartcn/build/libs/lucene-analysis-smartcn-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.cn.smart.TestBigramLookupZeroProbeStep
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/smartcn-hhmm-segmentation-reference/](../../models/smartcn-hhmm-segmentation-reference/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
