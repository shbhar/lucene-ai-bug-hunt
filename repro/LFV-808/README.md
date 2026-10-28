# TermInSetQuery#visit gives QueryVisitor an automaton supplier that throws IllegalArgumentException when any term is longer than 1000 bytes, so UnifiedHighlighter without WEIGHT_MATCHES fails on such a query

Bug LFV-808 · Area: search / TermInSetQuery visitor (consumeTermsMatching automaton supplier over the util/automaton string-union builder's 1000-byte term limit), reached from highlighter / UnifiedHighlighter without WEIGHT_MATCHES · Priority: low · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search with a TermInSetQuery of the two terms 'short' and a 1001-byte term on a field indexed with offsets, get the hit, and highlight it with a UnifiedHighlighter built with withWeightMatches(false), expecting 'a &lt;b&gt;short&lt;/b&gt; text', which is what the documented equivalent (a ConstantScoreQuery over a BooleanQuery of SHOULD TermQuery clauses) gives. Instead highlight throws IllegalArgumentException 'This builder doesn't allow terms that are larger than 1000 UTF-8 bytes', and any QueryVisitor that asks the query for its automaton gets the same exception. For more than one term TermInSetQuery#visit hands visitors a supplier that builds Automata.makeBinaryStringUnion over all terms, and that builder rejects terms longer than Automata.MAX_STRING_UNION_TERM_LENGTH (1000 bytes, a guard for its recursive construction), although the query itself accepts such terms and searches with them.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: highlight throws IllegalArgumentException 'This builder doesn't allow terms that are larger than 1000 UTF-8 bytes', and any QueryVisitor that asks the query for its automaton gets the same exception.

## Files

- [src/org/apache/lucene/search/uhighlight/TestTermInSetQueryLongTermAutomaton.java](src/org/apache/lucene/search/uhighlight/TestTermInSetQueryLongTermAutomaton.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.uhighlight.TestTermInSetQueryLongTermAutomaton
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
