# Japanese completion suggester (JapaneseCompletionAnalyzer / JapaneseCompletionFilter): words with a small tsu before shi, chi or tsu (雑誌, 決して, マッチ, 三つ) are never suggested for their Hepburn or input-method spellings zasshi, kesshite, macchi/matchi, mittsu, because romaji_map.txt maps ッシ only to 'ssi', ッチ only to 'tti' and ッツ only to 'ttu', while the base rows シ/チ/ツ list shi/chi/tsu and the neighbouring rows ッシャ and ッチャ do double the consonant ('ssha', 'ccha')

Bug LFV-849 · Area: analysis / kuromoji completion romanization (romaji_map.txt read by KatakanaRomanizer; JapaneseCompletionFilter, JapaneseCompletionAnalyzer) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-849.html](../../bugs/LFV-849.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 雑誌 ('magazine'), マッチ ('match') and 三つ ('three') in a document suggester with JapaneseCompletionAnalyzer, whose javadoc lists modified Hepburn among the supported romanizations, and expect typing 'zasshi', 'macchi' or 'mittsu' to suggest them, just as 'issho' suggests 一緒 and 'maccha' suggests 抹茶. Instead nothing is suggested: the bundled romaji_map.txt gives the small-tsu rows ッシ, ッチ and ッツ only the spellings 'ssi', 'tti' and 'ttu', although the base rows シ, チ, ツ list shi, chi, tsu and the neighbouring rows ッシャ and ッチャ list 'ssha' and 'ccha'. Only the Kunrei spellings (zassi, matti, mittu) find these words.

The test shows that the INDEX-mode `JapaneseCompletionAnalyzer` gives 雑誌 (ザッシ) only the Kunrei romaji `zassi`, never the modified-Hepburn `zasshi` (romaji_map.txt maps ッシ only to `ssi`), so typing "zasshi" cannot complete it.

## Expected vs actual

Expected `zasshi` among the index-side tokens; actual `[雑誌, zassi]`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/ja/TestCompletionSokuonHepburn.java](src/org/apache/lucene/analysis/ja/TestCompletionSokuonHepburn.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/kuromoji/build/libs/lucene-analysis-kuromoji-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ja.TestCompletionSokuonHepburn
```

The module(s) analysis-kuromoji also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/kuromoji-completion-romaji-map-vs-hepburn-kunrei-wapuro-tables/](../../models/kuromoji-completion-romaji-map-vs-hepburn-kunrei-wapuro-tables/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
