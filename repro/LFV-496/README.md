# CustomSeparatorBreakIterator is off by one next to the separator: following(p) skips the break right after a separator at p, preceding(p) returns p itself, a leading separator is not a break and a trailing one makes next return the end twice, so UnifiedHighlighter passages built on it start or end one segment off

Bug LFV-496 · Area: highlighter / UnifiedHighlighter break iterators (CustomSeparatorBreakIterator, alone or as the base of LengthGoalBreakIterator) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give the UnifiedHighlighter a CustomSeparatorBreakIterator('.'), alone or inside LengthGoalBreakIterator, and expect passages to start and end at the breaks its own next reports, just after each '.'. Instead following and preceding are off by one next to the separator: on 'Aa bb. Cc dd. Ee ff' (breaks 0, 6, 13, 19) following(5) returns 13 instead of 6 and preceding(13) returns 13 instead of 6, so a min-length-6 summary is 'Aa bb. Cc dd.' instead of 'Aa bb.' and the phrase 'dd ee' is shown as '&lt;b&gt;dd. Ee&lt;/b&gt; ff' instead of ' Cc &lt;b&gt;dd. Ee&lt;/b&gt; ff'. Nothing throws and the match is still highlighted; only the snippet boundary moves by one segment.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: following and preceding are off by one next to the separator: on 'Aa bb.

## Files

- [src/org/apache/lucene/search/uhighlight/TestCustomSeparatorBreakIteratorOffByOne.java](src/org/apache/lucene/search/uhighlight/TestCustomSeparatorBreakIteratorOffByOne.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed C0FFEE150
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=C0FFEE150 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.uhighlight.TestCustomSeparatorBreakIteratorOffByOne
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
