# NGramTokenizer and EdgeNGramTokenizer accept maxGram = Integer.MAX_VALUE, but their int buffer sizing wraps to a 1022-char read buffer and leaves the 16-char default term buffer, so the first text of 17 or more chars throws (ArrayIndexOutOfBoundsException up to 1021 chars, an unrelated IllegalArgumentException from 1022)

Bug LFV-1007 · Area: analysis-common / n-gram tokenizers (NGramTokenizer.init buffer sizing, shared by EdgeNGramTokenizer; NGramTokenizerFactory and EdgeNGramTokenizerFactory pass maxGramSize through unchecked) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build EdgeNGramTokenizer(1, Integer.MAX_VALUE) (or NGramTokenizer, or a factory with maxGramSize=2147483647) to mean 'all n-grams, no upper limit', and you expect the same tokens as with maxGram = text length, or else a clear IllegalArgumentException from the constructor, which validates maxGram and accepts it. Instead init computes the buffer sizes 2 * maxGram + 1024 and 2 * maxGram in int, which wrap to 1022 and -2, so the tokenizer gets a 1022-char read buffer and keeps its 16-char default term buffer. Texts of up to 16 chars come out right, but the first text of 17 or more chars throws ArrayIndexOutOfBoundsException 'Index 16 out of bounds for length 16' from incrementToken (from 1022 chars, an IllegalArgumentException about a buffer size the caller never passed).

The test shows that `EdgeNGramTokenizer(1, Integer.MAX_VALUE)` is accepted by its constructor but fails on the first 17-char text (the int buffer sizing in `NGramTokenizer.init` wraps and leaves the 16-char term buffer).

## Expected vs actual

Expected the 17 prefixes of `abcdefghijklmnopq` (as with maxGram = 17) or an `IllegalArgumentException` from the constructor; actual: `ArrayIndexOutOfBoundsException: Index 16 out of bounds for length 16` from `incrementToken`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/ngram/TestEdgeNGramTokenizerMaxGramIntegerMaxValue.java](src/org/apache/lucene/analysis/ngram/TestEdgeNGramTokenizerMaxGramIntegerMaxValue.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.ngram.TestEdgeNGramTokenizerMaxGramIntegerMaxValue
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/ngram-tokenizers-maxgram-above-input-length/](../../models/ngram-tokenizers-maxgram-above-input-length/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
