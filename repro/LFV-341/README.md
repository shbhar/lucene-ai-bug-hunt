# KatakanaRomanizer.romanize(CharsRef) ignores CharsRef.offset, so a CharsRef that is a slice of a larger char is romanized from chars[0, length) instead of the slice (latent: Lucene's own JapaneseCompletionFilter always passes offset 0)

Bug LFV-341 · Area: analysis / kuromoji (KatakanaRomanizer in org.apache.lucene.analysis.ja.completion, the romaji helper of JapaneseCompletionFilter) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call KatakanaRomanizer.getInstance.romanize(...) with a CharsRef that is a slice of a larger array, for example CharsRef.subSequence(2, 6) of 'ハシヤキュウ' (the slice 'ヤキュウ' at offset 2), and expect the romaji of the slice, [yakyuu], because CharsRef is documented as 'a slice (offset + length) into an existing char' and the same text at offset 0 gives [yakyuu]. Instead romanize reads chars[0, length), here 'ハシヤキ', and returns [hasiyaki, hashiyaki]; a slice at offset 1 of 'アトウキョウ' gives [atoukyo] instead of [toukyou], and one at offset 1 of 'xトウキョウt' gives instead of [toukyout]. There is no exception, with or without assertions, and Lucene's own JapaneseCompletionFilter is not affected because it always passes offset 0.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Romanize reads chars[0, length), here 'ハシヤキ', and returns [hasiyaki, hashiyaki]; a slice at offset 1 of 'アトウキョウ' gives [atoukyo] instead of [toukyou], and one at offset 1 of 'xトウキョウt' gives instead of [toukyout].

## Files

- [src/org/apache/lucene/analysis/ja/completion/TestKatakanaRomanizerCharsRefOffset.java](src/org/apache/lucene/analysis/ja/completion/TestKatakanaRomanizerCharsRefOffset.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.analysis.ja.completion.TestKatakanaRomanizerCharsRefOffset
```

The module(s) analysis-kuromoji also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
