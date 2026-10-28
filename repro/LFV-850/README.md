# Kuromoji's completion table romaji_map.txt has no rows for チェ, イェ, ツァ/ツィ/ツェ/ツォ, ディ/デュ and ウォ, although it has rows for their siblings (チャ/チュ/チョ, シェ/ジェ, ッイェ, ティ/テュ, ウィ/ウェ), so raw-romaji prefixes such as 'che', 'ye' and 'tsa' suggest nothing for words like チェック, イェール and ツァー

Bug LFV-850 · Area: analysis / kuromoji (JapaneseCompletionFilter and JapaneseCompletionAnalyzer romanization data: the bundled table romaji_map.txt that KatakanaRomanizer reads) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-850.html](../../bugs/LFV-850.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index ordinary loanwords such as チェック, イェール, ツァー, ディスク or ウォッカ with JapaneseCompletionAnalyzer and expect a user who types the usual romaji, che(kku), ye or tsa (or the IME keystrokes dhi, dhu, who), to get them as suggestions, because the analyzer lists (modified) Hepburn, Kunrei-shiki and Wapuro romanization as supported and its table already has these spellings for the sibling kana: シェ she, ジェ je, チャ cha, ティ thi, テュ thu, ウィ wi, ウェ we and even the doubled form ッイェ yye. Instead romaji_map.txt has no row for チェ, イェ, ツァ, ツィ, ツェ, ツォ, ディ, デュ or ウォ, so KatakanaRomanizer splits each into its base kana plus a small vowel (チ+ェ -&gt; tixe/chixe, イ+ェ -&gt; ixe, ツ+ァ -&gt; tuxa/tsuxa, デ+ィ -&gt; dexi, ウ+ォ -&gt; uxo) and these raw-romaji queries suggest nothing. Queries typed in kana, including partly composed ones such as ちぇっk, still match, because the index and query sides use the same fallback.

## Expected vs actual

- Expected: `chekku`, `ye` and `tsa` complete チェック, イェール and ツァー (and `dhisuku`, `dhuetto`, `whokka` complete ディスク, デュエット, ウォッカ), as `shea` completes シェア.
- Actual: None completes: the index-side romaji are only `tixekku`/`chixekku`, `ixeーru`, `tuxaー`/`tsuxaー`, `dexisuku`, `dexyuetto` and `uxokka`.

## Files

- [src/org/apache/lucene/analysis/ja/TestCompletionExtendedKatakana.java](src/org/apache/lucene/analysis/ja/TestCompletionExtendedKatakana.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/analysis/kuromoji/build/libs/lucene-analysis-kuromoji-$V.jar
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ja.TestCompletionExtendedKatakana
```

The module(s) analysis-kuromoji also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestJapaneseCompletionExtendedKatakana.java](TestJapaneseCompletionExtendedKatakana.java)
- [TestCompletionExtendedKatakana.java](TestCompletionExtendedKatakana.java)
- [3-TestCompletionExtendedKatakana.java](3-TestCompletionExtendedKatakana.java)

Formal models and scripts this bug's part of the hunt used: [../../models/kuromoji-completion-romaji-map-vs-hepburn-kunrei-wapuro-tables/](../../models/kuromoji-completion-romaji-map-vs-hepburn-kunrei-wapuro-tables/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
