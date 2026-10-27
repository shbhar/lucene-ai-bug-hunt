# SimplePatternTokenizer and SimplePatternSplitTokenizer restart a failed match one UTF-16 unit after a supplementary start character, so they can emit tokens that start or end inside a surrogate pair

Bug LFV-545 · Area: analysis / pattern tokenizers (analysis-common: SimplePatternTokenizer, SimplePatternSplitTokenizer and their simplePattern / simplePatternSplit factories) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You tokenize 'E x', where E is an emoji such as U+1F525, with SimplePatternTokenizer and the pattern E[a-z]+|[^E ]+ (E followed by letters, or a run of characters other than E and space) and expect the single token 'x' [3,4): the attempt at E dies at the space, and the tokenizer, which steps its automaton over code points, should move on to the next code point (PatternTokenizer and java.util.regex give exactly [x]). Instead it also emits a token made of E's lone low surrogate U+DD25 at offsets [1,2), which the index stores as U+FFFD, because the failed-attempt branch pushes back all but one UTF-16 unit and so restarts in the middle of the pair. SimplePatternSplitTokenizer has the same step: separator Ea|[^Ea ]+ on 'Eb' yields the lone high surrogate [0,1) instead of the token 'E' [0,2), and with separator [^a-zE]+|E! the text 'abEcd' becomes 'ab' plus a lone high surrogate, then 'cd', instead of the one token 'abEcd'; nothing throws.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: It also emits a token made of E's lone low surrogate U+DD25 at offsets [1,2), which the index stores as U+FFFD, because the failed-attempt branch pushes back all but one UTF-16 unit and so restarts in the middle of the pair.

## Files

- [src/org/apache/lucene/analysis/pattern/TestSimplePatternSurrogateRetry.java](src/org/apache/lucene/analysis/pattern/TestSimplePatternSurrogateRetry.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.pattern.TestSimplePatternSurrogateRetry
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
