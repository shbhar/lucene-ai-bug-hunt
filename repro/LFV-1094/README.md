# JapaneseTokenizer with the default discardPunctuation=true drops the dictionary nouns （株） （有） （社） （財） whole, Han letter included, because it tests only the token's first character for punctuation (known: #10430 / LUCENE-9390) (released)

Finding LFV-1094 · Area: analysis / kuromoji (JapaneseTokenizer discardPunctuation) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, reproduced on Lucene main but not yet independently re-verified, and not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You tokenize 東芝（株） with JapaneseTokenizer (default discardPunctuation=true) and expect [東芝, （株）], because only punctuation tokens are to be dropped and （株） is a dictionary noun. Instead you get [東芝]: the tokenizer discards any token whose first character is punctuation, so the noun and its Han letter 株 vanish; the same happens to （有）, （社） and （財） in all three modes.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: You get [東芝]: the tokenizer discards any token whose first character is punctuation, so the noun and its Han letter 株 vanish; the same happens to （有）, （社） and （財） in all three modes.

## Files

- [src/org/apache/lucene/analysis/ja/TestDiscardPunctuationDictionaryNoun.java](src/org/apache/lucene/analysis/ja/TestDiscardPunctuationDictionaryNoun.java) (JUnit test)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): reproduced.
- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/analysis/kuromoji/build/libs/lucene-analysis-kuromoji-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ja.TestDiscardPunctuationDictionaryNoun
```

The module(s) analysis-kuromoji also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
