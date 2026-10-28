# Kuromoji's completion table romaji_map.txt has three typo rows: the ッヘ row uses a hiragana へ (U+3078) in its key, so katakana ッヘ is romanized 'xtuhe' instead of 'hhe' and a word like ヨッヘン is not suggested for 'yohhen', and the ッスォ and ッドゥ rows carry the copied spellings 'suxo' and 'ddoxi'

Bug LFV-848 · Area: analysis / kuromoji (JapaneseCompletionFilter and JapaneseCompletionAnalyzer romanization table romaji_map.txt, read by KatakanaRomanizer) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-848.html](../../bugs/LFV-848.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index the katakana name ヨッヘン with JapaneseCompletionAnalyzer and type its romaji 'yohhen' into a suggester, and expect the name to be suggested, just as ヨッハン is suggested for 'yohhan'. Instead nothing is suggested: the table row meant for ッヘ, 'ッへ,hhe' (romaji_map.txt line 219), has the look-alike HIRAGANA LETTER HE (U+3078) in its key, and romanize only ever receives katakana or a-z, so the row can never match and ッヘ falls back to 'ッ'-&gt;'xtu' plus 'ヘ'-&gt;'he' (indexed as 'yoxtuhen'). The same table gives ッスォ the undoubled spelling 'suxo' (line 281, the spelling of スォ) and ッドゥ the spelling 'ddoxi' (line 289, copied from ッドィ) instead of 'ssuxo' and 'ddoxu'; a three-character table edit fixes all three.

## Expected vs actual

- Expected: `romanize(ッヘ)` = `[hhe]`, like `ッハ` → `[hha]`; `yohhen` completes ヨッヘン as `yohhan` completes ヨッハン, and `よっh` completes both.
- Actual: `romanize(ッヘ)` = `[xtuhe]`; ヨッヘン is indexed as `[ヨッヘン, yoxtuhen, yoxtuhenn]`, so neither `yohhen` nor `よっh` completes it; ッスォ and ッドゥ give `suxo` and `ddoxi`.

## Files

- [src/org/apache/lucene/analysis/ja/TestRomajiMapMalformedRows.java](src/org/apache/lucene/analysis/ja/TestRomajiMapMalformedRows.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.ja.TestRomajiMapMalformedRows
```

The module(s) analysis-kuromoji also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestJapaneseCompletionRomajiMapTypos.java](TestJapaneseCompletionRomajiMapTypos.java)
- [TestRomajiMapMalformedRows.java](TestRomajiMapMalformedRows.java)
- [TestRomajiMapRowsV1.java](TestRomajiMapRowsV1.java)

Formal models and scripts this bug's part of the hunt used: [../../models/kuromoji-completion-romaji-map-vs-hepburn-kunrei-wapuro-tables/](../../models/kuromoji-completion-romaji-map-vs-hepburn-kunrei-wapuro-tables/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
