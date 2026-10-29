# StandardTokenizer and UAX29URLEmailTokenizer with setMaxTokenLength(1) end the token stream at the first supplementary character (for example an emoji), so all later text of the field value is silently left out of the index

Bug LFV-132 · Area: analysis / StandardTokenizer and UAX29URLEmailTokenizer (JFlex no-buffer-expansion scanner) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You set maxTokenLength to 1 on StandardTokenizer (or StandardAnalyzer, UAX29URLEmailTokenizer, or a factory with maxTokenLength=1) and tokenize text that contains a code point above U+FFFF, for example 'ab U+10400 cd' or 'hello U+1F600 world'. You expect what the setter documents, that too-long tokens are chopped and emitted as multiple tokens, so the words after that character are still tokenized and end reports the input length as the final offset. Instead the 1-char scan buffer cannot hold the surrogate pair, the scanner's refill reports end of input at a token start, and the stream stops: [a, b] with final offset 3 of 8, [h, e, l, l, o], and nothing after the character is indexed.

The test shows that `StandardTokenizer` with `setMaxTokenLength(1)` ends the token stream at the first supplementary character (here U+10400), so later text is never tokenized.

## Expected vs actual

Expected for `"ab U+10400 cd"`: `c` and `d` are still emitted and the final offset is 8; actual: `[a, b]` and final offset 3, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/standard/TestStandardTokenizerMaxTokenLengthOne.java](src/org/apache/lucene/analysis/standard/TestStandardTokenizerMaxTokenLengthOne.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.standard.TestStandardTokenizerMaxTokenLengthOne
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
