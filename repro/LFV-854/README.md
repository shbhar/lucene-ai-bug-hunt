# Kuromoji completion suggester (JapaneseCompletionAnalyzer / JapaneseCompletionFilter, QUERY mode) suggests nothing while an input method shows a small tsu plus a pending c, j, f or v ('まっｃ' while typing maccha for 抹茶, 'ばっｊ' for bajji, 'すたっｆ' for sutaffu), because romaji_map.txt has the partial-input rows ッk..ッw but none for ッc, ッj, ッf or ッv, although its full rows double those letters (ッチャ ccha, ッジ jji, ッフ ffu, ッヴ vvu)

Bug LFV-854 · Area: analysis / kuromoji completion (JapaneseCompletionFilter QUERY-mode handling of mid-IME composition; the partial-input rows of romaji_map.txt, read by KatakanaRomanizer) · Priority: low · Impact: minor · Novelty: new

Bug page: [../../bugs/LFV-854.html](../../bugs/LFV-854.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build Japanese autocomplete with JapaneseCompletionAnalyzer, whose QUERY mode is documented as 'Input Method aware romanization' and joins a kana token with a still-pending romaji letter ('サッ' + 'k' =&gt; 'サッk', so 'さっｋ' keeps suggesting サッカー), and expect the same while a user types 'maccha' and the input method shows 'まっｃ'. Instead 'まっｃ' is romanized 'maxtuc' and nothing is suggested, and the same happens for 'ばっｊ' (bajji, バッジ) and 'ばっｆ' (baffa, バッファ), because the bundled romaji_map.txt has partial-input rows (small tsu plus one pending letter) only for k, g, s, z, t, d, h, b, p, m, y, r and w, although its own full rows double c, j, f and v (ッチャ ccha, ッジ jji, ッフ ffu, ッヴ vvu). The suggestions return one or two keystrokes later, when the input method converts the pending letters to kana.

## Expected vs actual

- Expected: `マッc` → `macc` (as `マッk` → `makk`), and `まっｃ`, `ばっｊ`, `すたっｆ` keep completing 抹茶, バッジ, スタッフ, as `さっｋ` completes サッカー.
- Actual: `マッc`, `マッj`, `マッf`, `マッv` → `maxtuc`, `maxtuj`, `maxtuf`, `maxtuv`; `まっｃ`, `ばっｊ`, `すたっｆ` complete nothing.

## Files

- [src/org/apache/lucene/analysis/ja/TestCompletionMidCompositionSokuon.java](src/org/apache/lucene/analysis/ja/TestCompletionMidCompositionSokuon.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.ja.TestCompletionMidCompositionSokuon
```

The module(s) analysis-kuromoji also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestJapaneseCompletionPendingLetter.java](TestJapaneseCompletionPendingLetter.java)
- [TestCompletionMidCompositionSokuon.java](TestCompletionMidCompositionSokuon.java)
- [TestSmallTsuPendingConsonant.java](TestSmallTsuPendingConsonant.java)

Formal models and scripts this bug's part of the hunt used: [../../models/kuromoji-completion-romaji-map-vs-hepburn-kunrei-wapuro-tables/](../../models/kuromoji-completion-romaji-map-vs-hepburn-kunrei-wapuro-tables/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
