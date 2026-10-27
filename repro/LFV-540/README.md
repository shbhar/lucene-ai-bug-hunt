# HyphenationTree.hyphenate does not shift its added start and end points past leading characters that are in none of the grammar's character classes, so HyphenationCompoundWordTokenFilter misses the subwords of tokens such as 3zimmerwohnung or "zimmerwohnung (the same word with a trailing quote decomposes)

Bug LFV-540 · Area: analysis-common / compound (HyphenationTree, HyphenationCompoundWordTokenFilter) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index text through HyphenationCompoundWordTokenFilter with a hyphenation grammar and the dictionary {zimmer, wohnung}, and expect '3zimmerwohnung' (one StandardTokenizer token) or '"zimmerwohnung' (WhitespaceTokenizer) to add the subwords zimmer and wohnung, as 'zimmerwohnung' and 'zimmerwohnung"' (trailing quote) do. Instead no subword is added: HyphenationTree.hyphenate skips the leading characters that are in no character class and shifts its inner hyphenation points past them, but it adds the start and end points unshifted (0 and the letter count), so '3zimmerwohnung' gets [0, 7, 13] instead of [1, 7, 14] and every candidate slice either starts on the '3' or loses the final letter. With two or more such characters the points are not even ascending ('!!abcde' gives [0, 6, 5]), and nothing throws.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: No subword is added: HyphenationTree.hyphenate skips the leading characters that are in no character class and shifts its inner hyphenation points past them, but it adds the start and end points unshifted (0 and the letter count), so '3zimmerwohnung' gets [0, 7, 13] instead of [1, 7, 14] and every candidate slice either starts on the '3' or loses the final letter.

## Files

- [src/org/apache/lucene/analysis/compound/TestHyphenationLeadingNonLetter.java](src/org/apache/lucene/analysis/compound/TestHyphenationLeadingNonLetter.java) (JUnit test)

## Run it

- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.analysis.compound.TestHyphenationLeadingNonLetter
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
