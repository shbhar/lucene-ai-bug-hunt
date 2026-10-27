# JapaneseTokenizer n-best output (nBestCost &gt; 0) with the default discardPunctuation=true gives a dictionary word that spans a discarded punctuation character (アイワ・コーポ) a position length one too long, so its path in the token graph skips the next word and a QueryBuilder query for アイワ・コーポ東京支店 makes 東京 optional

Bug LFV-616 · Area: analysis / kuromoji JapaneseTokenizer n-best output (analysis-common morph.ViterbiNBest.fixupPendingList) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You enable n-best output on JapaneseTokenizer (for example setNBestCost(2000)), keep the default discardPunctuation=true, analyze アイワ・コーポ東京支店 (a company name followed by 'Tokyo branch'), and expect a valid token graph in which the dictionary word アイワ・コーポ ends where 東京 starts, as it does with discardPunctuation=false. Instead ViterbiNBest.fixupPendingList counts the dropped ・ as an extra position, so アイワ・コーポ gets position length 3 instead of 2 and ends where 東京 ends, which breaks the analysis package rule that tokens with the same end position must have the same end offset. QueryBuilder then builds +((+アイワ +コーポ +東京) アイワ・コーポ) +支店 for an all-words query, and that query also matches a document about the Osaka branch (アイワ・コーポの大阪支店), which has no 東京.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: ViterbiNBest.fixupPendingList counts the dropped ・ as an extra position, so アイワ・コーポ gets position length 3 instead of 2 and ends where 東京 ends, which breaks the analysis package rule that tokens with the same end position must have the same end offset.

## Files

- [src/org/apache/lucene/analysis/ja/TestNBestPositionLengthDiscardedPunctuation.java](src/org/apache/lucene/analysis/ja/TestNBestPositionLengthDiscardedPunctuation.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/kuromoji/build/libs/lucene-analysis-kuromoji-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ja.TestNBestPositionLengthDiscardedPunctuation
```

The module(s) analysis-kuromoji also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
