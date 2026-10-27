# StandardTokenizer and UAX29URLEmailTokenizer with setMaxTokenLength(1) end the token stream at the first supplementary character (for example an emoji), so all later text of the field value is silently left out of the index

Bug LFV-132 · Area: analysis / StandardTokenizer and UAX29URLEmailTokenizer (JFlex no-buffer-expansion scanner) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You set maxTokenLength to 1 on StandardTokenizer (or StandardAnalyzer, UAX29URLEmailTokenizer, or a factory with maxTokenLength=1) and tokenize text that contains a code point above U+FFFF, for example 'ab U+10400 cd' or 'hello U+1F600 world'. You expect what the setter documents, that too-long tokens are chopped and emitted as multiple tokens, so the words after that character are still tokenized and end reports the input length as the final offset. Instead the 1-char scan buffer cannot hold the surrogate pair, the scanner's refill reports end of input at a token start, and the stream stops: [a, b] with final offset 3 of 8, [h, e, l, l, o], and nothing after the character is indexed.

## Expected vs actual

- Expected: You expect what the setter documents, that too-long tokens are chopped and emitted as multiple tokens, so the words after that character are still tokenized and end reports the input length as the final offset.
- Actual: The 1-char scan buffer cannot hold the surrogate pair, the scanner's refill reports end of input at a token start, and the stream stops: [a, b] with final offset 3 of 8, [h, e, l, l, o], and nothing after the character is indexed.

## Files

- [src/org/apache/lucene/analysis/standard/TestStandardTokenizerMaxTokenLengthOne.java](src/org/apache/lucene/analysis/standard/TestStandardTokenizerMaxTokenLengthOne.java) (JUnit test)
- [src/org/apache/lucene/analysis/email/TestUAX29URLEmailTokenizerMaxTokenLengthOne.java](src/org/apache/lucene/analysis/email/TestUAX29URLEmailTokenizerMaxTokenLengthOne.java) (JUnit test)
- [src/maxlen1/MaxTokenLengthOneMain.java](src/maxlen1/MaxTokenLengthOneMain.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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

# run 1: assertions on, seed 28CA5EF0
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=28CA5EF0 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.standard.TestStandardTokenizerMaxTokenLengthOne \
       --select-class org.apache.lucene.analysis.email.TestUAX29URLEmailTokenizerMaxTokenLengthOne

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" maxlen1.MaxTokenLengthOneMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
