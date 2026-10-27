# PassageSelector.pickBest (MatchHighlighter) silently drops the highlight passages of permitted ranges that start after offset 0 when maxPassageWindow is Integer.MAX_VALUE: the int window shift 'to += range.from - from' wraps negative, so the second and later values of a multi-valued field lose their snippets (the residue of the LUCENE-10135 overflow fix)

Bug LFV-387 · Area: highlighter / matchhighlight PassageSelector (MatchHighlighter, FieldValueHighlighters) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You highlight a document whose stored field has two values, 'aaaa foo' and 'bbbb foo', for the query text:foo with MatchHighlighter and FieldValueHighlighters.highlighted(Integer.MAX_VALUE, 10,...), meaning 'no limit on the passage size', and expect both values highlighted, [aaaa &gt;foo&lt;, bbbb &gt;foo&lt;], as a window of 1000 gives. Instead you get only [aaaa &gt;foo&lt;], with no error: PassageSelector.pickBest centres each window in long arithmetic but then shifts it into the value's range with the int statement 'to += range.from - from', which wraps negative for a range that starts after offset 0, so the match in the second value gets no passage. If only the second value matches, both values come back unhighlighted ([bbbb bar, aaaa foo] instead of [aaaa &gt;foo&lt;]).

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: You get only [aaaa &gt;foo&lt;], with no error: PassageSelector.pickBest centres each window in long arithmetic but then shifts it into the value's range with the int statement 'to += range.from - from', which wraps negative for a range that starts after offset 0, so the match in the second value gets no passage.

## Files

- [src/org/apache/lucene/search/matchhighlight/TestPassageSelectorMaxWindowLaterRange.java](src/org/apache/lucene/search/matchhighlight/TestPassageSelectorMaxWindowLaterRange.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.matchhighlight.TestPassageSelectorMaxWindowLaterRange
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/overflow-at-scale-2/](../../models/overflow-at-scale-2/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
