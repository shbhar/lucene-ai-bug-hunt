# WikipediaTokenizer silently drops letters and digits from plain text that Unicode 3.0 already had (µ in '5 µm', ª/º in '1ª'/'2º', fullwidth digits and letters, ligatures such as ﬁ, Yi syllables, Arabic presentation forms; 2,083 BMP code points that ClassicTokenizer keeps) because its LETTER and DIGIT macros are hard-coded 2008 range lists

Bug LFV-721 · Area: analysis-common / wikipedia: WikipediaTokenizer (JFlex grammar character classes, WikipediaTokenizerImpl.jflex LETTER/DIGIT macros) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You tokenize ordinary plain text with WikipediaTokenizer, for example '5 µm', '1ª edición 2º piso', '２０２６ year', 'ﬁle' or the Yi text 'ꀀꀁ', and expect every letter and digit to end up in a token, as it does with ClassicTokenizer (whose grammar is also pinned to Unicode 3.0) and StandardTokenizer. Instead WikipediaTokenizer skips these characters without an error: '5 µm' gives [5, m], '1ª 2º' gives [1, 2], 'ﬁle' gives [le], and '２０２６', 'ＡＢＣ' and 'ꀀꀁ' give no token; an exhaustive check of the BMP finds 2,083 letters and digits that ClassicTokenizer keeps and WikipediaTokenizer drops. The cause is the grammar: ALPHANUM is built from LETTER and DIGIT macros that are fixed range lists copied in 2008 (WikipediaTokenizerImpl.jflex:154,:156), which miss U+00AA, U+00B5, U+00BA and every letter or digit from U+2000 up to the halfwidth forms outside the CJ and Korean ranges, so these characters fall into the '[^]' ignore rule (:216); StandardTokenizer had the same lists and replaced them with [:letter:]/[:digit:] in 2.4 (LUCENE-1126), but the WikipediaTokenizer copy was never updated.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: WikipediaTokenizer skips these characters without an error: '5 µm' gives [5, m], '1ª 2º' gives [1, 2], 'ﬁle' gives [le], and '２０２６', 'ＡＢＣ' and 'ꀀꀁ' give no token; an exhaustive check of the BMP finds 2,083 letters and digits that ClassicTokenizer keeps and WikipediaTokenizer drops.

## Files

- [src/org/apache/lucene/analysis/wikipedia/TestWikipediaTokenizerLetterRanges.java](src/org/apache/lucene/analysis/wikipedia/TestWikipediaTokenizerLetterRanges.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.wikipedia.TestWikipediaTokenizerLetterRanges
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
