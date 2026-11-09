# JapaneseTokenizer with discardPunctuation=true (the default, and the only setting JapaneseAnalyzer uses) still emits the punctuation-only segments of a user-dictionary entry, so an entry that segments a dotted name (レオナルド・ダ・ヴィンチ as レオナルド ・ ダ ・ ヴィンチ) indexes '・' terms that take positions, and phrase queries for parts of the name such as "ダ・ヴィンチ" stop matching (since 3.6.0)

Finding LFV-1458 · Area: analysis-kuromoji / JapaneseTokenizer user-dictionary output (ViterbiNBest backtrace USER branch and the n-best registerNode copy) vs discardPunctuation · Priority: medium · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1458.html](../../bugs/LFV-1458.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give JapaneseTokenizer (or the stock JapaneseAnalyzer) a user dictionary with the well-formed entry 'レオナルド・ダ・ヴィンチ,レオナルド ・ ダ ・ ヴィンチ,レオナルド ・ ダ ・ ヴィンチ,カスタム名詞' and keep discardPunctuation=true, whose javadoc says 'true if punctuation tokens should be dropped from the output'; you expect [レオナルド, ダ, ヴィンチ], which is what the tokenizer gives for the same text without the entry. Instead it emits [レオナルド, ・, ダ, ・, ヴィンチ], exactly as with discardPunctuation=false, because the code that expands a user entry into its segments (ViterbiNBest.backtrace, and registerNode for n-best output) never checks for punctuation, while the other token paths do. The kept dots take positions, so for a one-document index 'レオナルド・ダ・ヴィンチの絵' the phrase queries "ダ・ヴィンチ", "ダ ヴィンチ" and "レオナルド ダ ヴィンチ" find the document without the entry and miss it with the entry, with no error.

## Expected vs actual

- Expected: `[ダ, ヴィンチ]`: the dot dropped, as for the same text without the entry.
- Actual: `org.junit.ComparisonFailure: term 1 expected:<[ヴィンチ]> but was:<[・]>`, with assertions on and off.

## Files

- [src/org/apache/lucene/analysis/ja/TestUserDictionaryPunctuationSegment.java](src/org/apache/lucene/analysis/ja/TestUserDictionaryPunctuationSegment.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
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
     execute --select-class org.apache.lucene.analysis.ja.TestUserDictionaryPunctuationSegment
```

The module(s) analysis-kuromoji also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestUserDictionaryPunctuationSegment.java](TestUserDictionaryPunctuationSegment.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
