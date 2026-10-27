# ICUTokenizer loses a supplementary character (an emoji, a CJK Extension B ideograph) whose surrogate pair straddles the end of its 4096-char read chunk in text with no whitespace, because refill cuts the pair in half

Bug LFV-445 · Area: analysis / ICU (ICUTokenizer chunked buffer refill; ICUTokenizerFactory 'icu') · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-445.html](../../bugs/LFV-445.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a long Chinese paragraph with no spaces or line breaks through the stock ICUTokenizer, and it contains an emoji or a rare ideograph such as U+20000; you expect that character to be a token wherever it falls, as it is one position earlier or later. Instead, when its first UTF-16 half is the last char of the tokenizer's 4096-char read chunk, refill gives the word breaker a chunk that ends in a lone high surrogate and starts the next chunk with the lone low surrogate; both halves get rule status 0 and are skipped, so the character is in no token. A TermQuery for it finds 0 documents instead of 1, with no error.

## Expected vs actual

- Expected: U+20000 at char 4095 is the token `𠀀[4095,4097)`, and `TermQuery(U+20000)` finds all 3 documents.
- Actual: `U+20000 at char 4095 is in no token; tokens near it: [中文[4092,4094), 中[4094,4095), 中文[4097,4099), …]`, and the TermQuery finds 2 documents.

## Files

- [src/org/apache/lucene/analysis/icu/segmentation/TestICUTokenizerRefillSurrogatePair.java](src/org/apache/lucene/analysis/icu/segmentation/TestICUTokenizerRefillSurrogatePair.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/icu/build/libs/lucene-analysis-icu-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.icu.segmentation.TestICUTokenizerRefillSurrogatePair
```

The module(s) analysis-icu also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestICUTokenizerSurrogateAtChunkEnd.java](TestICUTokenizerSurrogateAtChunkEnd.java)
- [TestICUTokenizerRefillSurrogatePair.java](TestICUTokenizerRefillSurrogatePair.java)
- [TestAuditIcuChunkEdgeSearch.java](TestAuditIcuChunkEdgeSearch.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
