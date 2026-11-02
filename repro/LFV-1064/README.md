# JapaneseKatakanaUppercaseFilterFactory and JapaneseHiraganaUppercaseFilterFactory do not override normalize(), so CustomAnalyzer.normalize leaves small kana unmapped and query-parser prefix, wildcard, regexp, fuzzy and range terms that contain them silently miss the indexed documents

Finding LFV-1064 · Area: analysis-kuromoji / sutegana (small kana) token filter factories (JapaneseKatakanaUppercaseFilterFactory, JapaneseHiraganaUppercaseFilterFactory) and Analyzer.normalize via CustomAnalyzer, which the query parsers use for multi-term queries · Priority: medium · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1064.html](../../bugs/LFV-1064.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a CustomAnalyzer with the kuromoji small-kana filters (withTokenizer("keyword").addTokenFilter("japaneseKatakanaUppercase").addTokenFilter("japaneseHiraganaUppercase")), and indexing キャットちょっと stores the term キヤツトちよつと. You expect Analyzer.normalize("f", "キャットちょっと") to return キヤツトちよつと, 'the representation that it would have in the index', as it does for the similar cjkWidth filter. Instead it returns the input unchanged, because both factories override create() but not normalize() and CustomAnalyzer.normalize only chains each factory's normalize(), so query-parser multi-term terms keep their small kana and silently miss: a document キャット is found by the term query キャット but not by the prefix query キャ* or the range [キャット TO キャット].

## Expected vs actual

- Expected: `analyzer.normalize("f", "キャちょ")` returns `キヤちよ`, the term the index holds for `キャちょ`.
- Actual: It returns `キャちょ` unchanged: `ComparisonFailure: expected:<キ[ヤちよ]> but was:<キ[ャちょ]>`, with assertions on and off.

## Files

- [src/org/apache/lucene/analysis/ja/TestSuteganaFactoriesNormalize.java](src/org/apache/lucene/analysis/ja/TestSuteganaFactoriesNormalize.java) (JUnit test)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): still reproduces.
- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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
java -ea -esa -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ja.TestSuteganaFactoriesNormalize
```

The module(s) analysis-kuromoji also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestSuteganaFactoriesNormalize.java](TestSuteganaFactoriesNormalize.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
