# SimplePatternTokenizer and SimplePatternSplitTokenizer join an unpaired high surrogate with the character after it, so a following space no longer separates two words and a following letter is lost

Finding LFV-1454 · Area: analysis / pattern tokenizers (analysis-common: SimplePatternTokenizer, SimplePatternSplitTokenizer and their simplePattern / simplePatternSplit factories) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1454.html](../../bugs/LFV-1454.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You tokenize 'a' U+D83D ' b' (an unpaired high surrogate, as left by cutting a string inside an emoji, then a space and 'b') with SimplePatternTokenizer('[^ ]+') or SimplePatternSplitTokenizer('[ ]+') and expect two tokens, 'a'+U+D83D [0,2) and 'b' [3,4): that is what String.split("[ ]+"), PatternTokenizer and WhitespaceTokenizer give, and what the tokenizers' own automaton accepts under CharacterRunAutomaton.run. Instead both emit one token 'a'+U+D83D+' b' [0,4), because nextCodePoint() pairs the high surrogate with whatever unit follows it without checking for a low surrogate, so the space becomes part of the made-up code point U+11820 and stops being a separator; with a letters-only pattern the next letter is lost the same way (SimplePatternTokenizer('[a-z]+') on U+D83D 'ab' gives 'b' [2,3) instead of 'ab' [1,3)), and nothing throws.

## Expected vs actual

- Expected: Two tokens, `hi\uD83D` [0,3) and `yo` [4,6), final offset 6.
- Actual: One token, `hi\uD83D yo` [0,6): `term 0 expected:<hi?[]> but was:<hi?[ yo]>` (`?` is U+D83D).

## Files

- [src/org/apache/lucene/analysis/pattern/TestSimplePatternUnpairedHighSurrogate.java](src/org/apache/lucene/analysis/pattern/TestSimplePatternUnpairedHighSurrogate.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.pattern.TestSimplePatternUnpairedHighSurrogate
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestSimplePatternUnpairedHighSurrogate.java](TestSimplePatternUnpairedHighSurrogate.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
