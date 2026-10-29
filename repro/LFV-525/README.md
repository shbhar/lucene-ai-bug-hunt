# SimplePatternTokenizer and SimplePatternSplitTokenizer stop scanning when a non-matching lookahead reaches the end of the input, so later tokens are dropped (or later separators ignored)

Bug LFV-525 · Area: analysis / pattern tokenizers (analysis-common: SimplePatternTokenizer, SimplePatternSplitTokenizer and their simplePattern / simplePatternSplit factories) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-525.html](../../bugs/LFV-525.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You tokenize 'say "hello world' with SimplePatternTokenizer and the pattern "[^"]*"|[a-z]+ (a quoted string or a word) and expect [say, hello, world], because the javadoc says the longest match at each start point is the next token and the class otherwise skips one character and tries again (PatternTokenizer with the same regex gives these three tokens). Instead you get only [say]: the lookahead from the unclosed quote runs to the end of the input without ever matching, and that end-of-input branch ends tokenization instead of retrying at the next character, so 'hello' and 'world' are never indexed (minimal case: abc|b on 'ab' gives instead of [b]). SimplePatternSplitTokenizer has the same branch and emits the whole rest as one token: separator &lt;[^&gt;]*&gt;|+ on 'x &lt; y' gives [x, '&lt; y'] instead of String.split's [x, &lt;, y]; nothing throws.

The test shows that SimplePatternTokenizer stops scanning when a lookahead that never matched reaches the end of the input, instead of retrying at the next character as it does when the lookahead dies earlier.

## Expected vs actual

Expected pattern `abc|b` on `ab` to give `[b]` (as on `abx`); actual `[]` (`token 0 does not exist`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/pattern/TestSimplePatternLookaheadAtEnd.java](src/org/apache/lucene/analysis/pattern/TestSimplePatternLookaheadAtEnd.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.pattern.TestSimplePatternLookaheadAtEnd
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
