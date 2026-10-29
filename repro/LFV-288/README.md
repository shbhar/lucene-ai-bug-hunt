# UnifiedHighlighter.Builder#withFlags is silently ignored: the highlighter always takes its flags from the four boolean builder options

Bug LFV-288 · Area: highlighter / UnifiedHighlighter builder and highlight flags (UnifiedHighlighter.evaluateFlags(Builder) never reads Builder.flags) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a UnifiedHighlighter with Builder#withFlags(EnumSet.of(MULTI_TERM_QUERY)), whose javadoc says the set 'will override' the four boolean builder options, and expect it to behave like the same flags chosen through those options: for a PhraseQuery 'alpha beta' on 'alpha beta. alpha gamma.' that gives '&lt;b&gt;alpha&lt;/b&gt; &lt;b&gt;beta&lt;/b&gt;. &lt;b&gt;alpha&lt;/b&gt; gamma.'. Instead the constructor's evaluateFlags(Builder) tests the highlighter's own flags field, which is still null at that point, rather than the builder's, so the set passed to withFlags is never read: getFlags returns the defaults [PHRASES, MULTI_TERM_QUERY, PASSAGE_RELEVANCY_OVER_SPEED, WEIGHT_MATCHES] and the phrase is highlighted strictly as '&lt;b&gt;alpha beta&lt;/b&gt;. alpha gamma.'. Every set is ignored the same way, including the empty set (a PrefixQuery is still highlighted), with or without assertions.

The test shows that `UnifiedHighlighter.Builder#withFlags` is ignored: with `withFlags(EnumSet.of(MULTI_TERM_QUERY))` the highlighter still uses the default flags (strict phrase highlighting).

## Expected vs actual

Expected `<b>alpha</b> <b>beta</b> <b>alpha</b> gamma` for PhraseQuery `"alpha beta"` on `alpha beta alpha gamma`; actual: `<b>alpha beta</b> alpha gamma`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/uhighlight/TestUnifiedHighlighterBuilderWithFlags.java](src/org/apache/lucene/search/uhighlight/TestUnifiedHighlighterBuilderWithFlags.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.uhighlight.TestUnifiedHighlighterBuilderWithFlags
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
