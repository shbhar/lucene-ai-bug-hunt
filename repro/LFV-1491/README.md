# With assertions enabled, the default UnifiedHighlighter fails an assertion in OffsetsEnum.OfMatchesIteratorWithSubs (its source comment: 'miOE should follow cached entries') when a span clause and another clause of a disjunction match the same token (body:qu* OR spanTerm(body:quick) on 'the quick brown fox'): AssertionError with -ea, the expected snippet without assertions

Finding LFV-1491 · Area: highlighter / UnifiedHighlighter (Matches-based offsets, HighlightFlag.WEIGHT_MATCHES, the default since 9.0.0) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1491.html](../../bugs/LFV-1491.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You highlight the text 'the quick brown fox' with the default UnifiedHighlighter for the query body:qu* OR spanTerm(body:quick), in a JVM that runs with assertions enabled. You expect 'the &lt;b&gt;quick&lt;/b&gt; brown fox', which is what the same two clauses in the other order return and what a JVM without assertions returns. Instead the call throws an AssertionError without a message from Lucene's own assert statement, whose source comment reads 'miOE should follow cached entries': both clauses match the token 'quick', and the highlighter's queue puts the prefix clause's match ahead of the span clause's cached sub-match, an order that the queue's comparator allows and the assert rules out. With assertions off, which is how production JVMs run, the same call returns the expected snippet: for this query nothing is wrong there.

## Expected vs actual

- Expected: `the <b>quick</b> brown fox`, as the other clause order and a JVM without assertions return.
- Actual: `java.lang.AssertionError` at `OffsetsEnum$OfMatchesIteratorWithSubs.nextWhenMatchesIterator(OffsetsEnum.java:216)`, with assertions on.

## Files

- [src/org/apache/lucene/search/uhighlight/TestUnifiedHighlighterPrefixAndSpanOnSameToken.java](src/org/apache/lucene/search/uhighlight/TestUnifiedHighlighterPrefixAndSpanOnSameToken.java) (JUnit test)

## Run it

- Tested on Lucene main f2b061406c4b (a commit of 2026-11-19): still reproduces.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced.
- What the test does: it builds a BooleanQuery of two SHOULD clauses, PrefixQuery body:qu* and SpanTermQuery body:quick, and calls highlightWithoutSearcher on the text 'the quick brown fox' with a UnifiedHighlighter built with its default flags over a StandardAnalyzer. It asserts the snippet 'the &lt;b&gt;quick&lt;/b&gt; brown fox'. With assertions on, as in the recipe (-ea -esa), the highlight call throws java.lang.AssertionError, without a message, from Lucene's own assert statement in OffsetsEnum.OfMatchesIteratorWithSubs.nextWhenMatchesIterator (OffsetsEnum.java:216 on main) before the test's own check runs; 'miOE should follow cached entries' is the comment on that source line, not a message in the output. With assertions off (-da -dsa -Dtests.asserts=false) the test passes: the highlighter returns the expected snippet. For this finding a passing run without assertions is the expected outcome and does not mean that the finding is gone. The test uses no index and no fault injection; it needs the highlighter, memory and queries jars beside core, as the recipe lists them.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.uhighlight.TestUnifiedHighlighterPrefixAndSpanOnSameToken
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestUnifiedHighlighterPrefixAndSpanOnSameToken.java](TestUnifiedHighlighterPrefixAndSpanOnSameToken.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
