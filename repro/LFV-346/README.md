# JapaneseCompletionFilter expands a reading into every combination of romaji spellings with no bound, so 24-32 kana (for example 32 x "ン", or 32 x "し" in QUERY mode) throw OutOfMemoryError inside one incrementToken call

Bug LFV-346 · Area: analysis / kuromoji (JapaneseCompletionFilter and JapaneseCompletionAnalyzer romanization, KatakanaRomanizer) · Priority: medium · Impact: crash · Novelty: new

Bug page: [../../bugs/LFV-346.html](../../bugs/LFV-346.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyze a short Japanese string with JapaneseCompletionAnalyzer or JapaneseCompletionFilter, for example 32 x "ン" when indexing or 32 x "し" as an auto-complete query, and expect the surface token plus a bounded set of romanized spellings. Instead KatakanaRomanizer.romanize builds every combination of the alternative spellings of each ambiguous kana (2^n for シ ジ チ ツ ヅ フ ヲ ン, 3^n for ヂ; for ン most are duplicates: 2^n tokens but only n+1 distinct strings), eagerly and with no limit, and the filter buffers all of them before it returns the first token. So 20 x "ン" already gives 1,048,577 tokens at one position, 24-32 kana throw OutOfMemoryError, a LimitTokenCountFilter placed after the filter cannot help, and an IndexWriter that analyzes such a field closes.

The test shows that JapaneseCompletionAnalyzer expands every combination of romaji spellings eagerly, so "ン" x 8 gives 2^8 = 256 romanized tokens at one position, nearly all duplicates (the same 2^n growth exhausts the heap at 24-32 kana).

## Expected vs actual

Expected: each token once, 10 tokens (the surface form and n x 8.. n x 16). Actual (-ea and -da): 257 tokens (expected:&lt;10&gt; but was:&lt;257&gt;).

## Files

- [src/org/apache/lucene/analysis/ja/TestJapaneseCompletionDuplicateRomaji.java](src/org/apache/lucene/analysis/ja/TestJapaneseCompletionDuplicateRomaji.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.ja.TestJapaneseCompletionDuplicateRomaji
```

The module(s) analysis-kuromoji also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/japanese-completion-fifo/](../../models/japanese-completion-fifo/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
