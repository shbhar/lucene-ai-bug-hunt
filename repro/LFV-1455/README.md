# CJKBigramFilter never applies the position increment of its CJK input tokens to the bigrams and unigrams it builds from them, so the hole that a StopFilter (or any token-removing filter) placed before it leaves in front of CJK text disappears and an exact phrase matches across the removed word, while a non-CJK token behind the same hole keeps it; since 3.6.0

Finding LFV-1455 · Area: analysis / CJK (CJKBigramFilter position increments; a custom chain with a token-removing filter before cjkBigram) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1455.html](../../bugs/LFV-1455.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an analyzer StandardTokenizer &gt; StopFilter(['the']) &gt; CJKBigramFilter and analyze 'foo the 東京'. You expect [foo +1, 東京 +2]: the removed 'the' leaves a one-position hole, as it does for 'foo the bar', which the same chain turns into [foo +1, bar +2], so that the phrase 'foo 東京' does not match. Instead CJKBigramFilter builds the bigram 東京 from a cleared attribute state and never copies the position increment of the CJK input token, so the stream is [foo +1, 東京 +1] and, with this analyzer at index and query time, the phrase 'foo 東京' matches the document 'foo the 東京' (the phrase 'foo bar' does not match 'foo the bar').

## Expected vs actual

- Expected: `foo` +1, `東京` +2: the hole left by the removed "the", as `bar` gets in "foo the bar".
- Actual: `AssertionError: posIncrement 1 term=東京 expected:<2> but was:<1>`, with assertions on and off.

## Files

- [src/org/apache/lucene/analysis/cjk/TestCJKBigramFilterHole.java](src/org/apache/lucene/analysis/cjk/TestCJKBigramFilterHole.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.cjk.TestCJKBigramFilterHole
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestCJKBigramFilterHole.java](TestCJKBigramFilterHole.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
