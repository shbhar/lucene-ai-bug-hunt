# PolishAnalyzer and StempelFilter can emit a term with an unpaired surrogate: the Stempel patch command is applied by UTF-16 unit and overwrites or deletes half of a supplementary character that stands directly in front of a short ending

Finding LFV-1492 · Area: analysis / stempel (StempelStemmer, StempelFilter, PolishAnalyzer and the stempelPolishStem factory; org.egothor.stemmer Diff and MultiTrie2) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyze the Polish word 'Pije' with a final e-ogonek ('I drink'), written with a decorative first letter (U+1D4DF, MATHEMATICAL BOLD SCRIPT CAPITAL P), with the stock PolishAnalyzer. You expect a term made of whole characters: a stem or the unchanged word. Instead the term is the lone high surrogate U+D835 followed by 's', a-ogonek and c-acute: the stemmer's patch command for the ending counts UTF-16 units, and its last replacement overwrites the second half of the two-unit letter. The term is not valid UTF-16 and the index stores it with U+FFFD in place of the cut letter; nothing throws and no search misses a document, because the same text gives the same term at query time.

## Expected vs actual

- Expected: You expect a term made of whole characters: a stem or the unchanged word.
- Actual: The term is the lone high surrogate U+D835 followed by 's', a-ogonek and c-acute: the stemmer's patch command for the ending counts UTF-16 units, and its last replacement overwrites the second half of the two-unit letter.

## Files

- [src/org/apache/lucene/analysis/pl/TestPolishAnalyzerSurrogatePair.java](src/org/apache/lucene/analysis/pl/TestPolishAnalyzerSurrogatePair.java) (JUnit test)

## Run it

- Tested on Lucene main f2b061406c4b (a commit of 2026-11-19): still reproduces.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced.
- What the test does: it analyzes the five UTF-16 units U+D835 U+DCDF 'i' 'j' U+0119 (the letter U+1D4DF, then 'ij' and e-ogonek) with a stock PolishAnalyzer and reads the first term. It asserts that the term is valid UTF-16 (UnicodeUtil.validUTF16String) and fails with 'term has an unpaired surrogate'; the same happens with and without assertions. It checks the term text only: the U+FFFD in the indexed term bytes, which the description above mentions, is not part of this test, and nothing is indexed. No fault injection and no extra heap are needed; beside core the recipe needs the analysis-common and analysis-stempel jars, which it lists.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/analysis/stempel/build/libs/lucene-analysis-stempel-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.pl.TestPolishAnalyzerSurrogatePair
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
