# StandardTokenizer (StandardAnalyzer's tokenizer) and UAX29URLEmailTokenizer misapply the UAX #29 Hebrew quote rules WB7a-WB7c next to other word characters: a Hebrew letter + ASCII apostrophe swallows a following digit or underscore (BET'2 is one term), an ASCII apostrophe after a Hebrew letter that ends a mid-word chain is dropped (HE.SHIN' -&gt; HE.SHIN), and a word with an ASCII quotation mark between Hebrew letters is split when it also contains a mid-word '.', ':' or quote (ALEF.TSADI"LAMED, TAV"ALEF.BET)

Bug LFV-734 · Area: analysis / StandardTokenizer JFlex word rule (the <ALPHANUM> rule of core StandardTokenizerImpl.jflex; the same rule in analysis-common UAX29URLEmailTokenizerImpl.jflex) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-734.html](../../bugs/LFV-734.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index Hebrew text with the stock StandardAnalyzer, whose StandardTokenizer says it implements the UAX #29 word-break rules, among them WB7a-WB7c, which keep an ASCII apostrophe after a Hebrew letter (BET') and an ASCII quotation mark between two Hebrew letters (TAV"ALEF) inside a word, and you expect them to combine with the other rules as the annex and ICU do: BET'2 gives [BET', 2], HE.SHIN' gives [HE.SHIN'], and TAV"ALEF.BET and ALEF.TSADI"LAMED are single words. Instead the grammar writes 'Hebrew letter + quote' as a separate piece that may be glued to any following piece but cannot continue a letter chain, so BET'2 becomes one term (a search for 2 misses the document), HE.SHIN' loses the apostrophe, and the quoted words are split into [TAV"ALEF, BET] and [ALEF.TSADI, LAMED]. StandardTokenizer and UAX29URLEmailTokenizer behave the same, with no error.

The test shows that `StandardAnalyzer` (StandardTokenizer) keeps a digit after a Hebrew letter plus ASCII apostrophe in the same term: `"\u05D1'2"` (BET ' 2).

## Expected vs actual

Expected (UAX #29 WB7a joins only BET and the apostrophe, nothing joins the apostrophe and 2): terms [BET', 2]. Actual: one term BET'2 (`term 0 expected:<ב'[]> but was:<ב'[2]>`).

## Files

- [src/org/apache/lucene/analysis/standard/TestStandardTokenizerHebrewApostrophe.java](src/org/apache/lucene/analysis/standard/TestStandardTokenizerHebrewApostrophe.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.standard.TestStandardTokenizerHebrewApostrophe
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/uax29-word-boundaries-reference/](../../models/uax29-word-boundaries-reference/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
