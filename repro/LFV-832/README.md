# FastVectorHighlighter drops a match at a position that holds stacked tokens (index-time synonyms, stems) when the token that sorts first there starts a query phrase that does not continue: FieldPhraseList tries only the first stacked token with any query-map entry, so dogs OR "dog house" on 'my dogs' (synonym dog stacked on dogs) highlights nothing (a LUCENE-5538 regression, 4.7.1 on)

Bug LFV-832 · Area: highlighter / vectorhighlight FieldPhraseList and FieldTermStack (FastVectorHighlighter phrase search over stacked tokens) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 'my dogs' with an index-time synonym filter that stacks 'dog' on 'dogs' (term vectors with positions and offsets), search dogs OR "dog house", which matches through dogs (Weight#matches reports offsets 3-7), and call FastVectorHighlighter.getBestFragment, expecting 'my &lt;b&gt;dogs&lt;/b&gt;', which the TermQuery alone gives. Instead you get null, with no error. FieldPhraseList starts a candidate with the first token at that position that has any entry in the query's phrase map ('dog', which sorts first and only starts the phrase "dog house"), finds that the phrase does not continue, adds nothing and never tries 'dogs' at the same position; Lucene 4.7.0 highlights this correctly, and the LUCENE-5538 fix in 4.7.1 introduced the loop.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: You get null, with no error.

## Files

- [src/org/apache/lucene/search/vectorhighlight/TestFvhStackedTokenShadowsTerm.java](src/org/apache/lucene/search/vectorhighlight/TestFvhStackedTokenShadowsTerm.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.vectorhighlight.TestFvhStackedTokenShadowsTerm
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
