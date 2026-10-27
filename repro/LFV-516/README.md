# Default UnifiedHighlighter (WEIGHT_MATCHES) rewrites the query again on its own per-leaf or one-document reader, so statistics-dependent queries such as CommonTermsQuery lose their highlights

Bug LFV-516 · Area: highlighter / UnifiedHighlighter WEIGHT_MATCHES path (FieldOffsetStrategy) with statistics-dependent query rewrites (CommonTermsQuery, MoreLikeThisQuery) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search with CommonTermsQuery(SHOULD, MUST, 0.5) over 'the' (in 5 of 6 documents) and 'fox' (2 of 6), which the searcher rewrites to +fox the, and highlight the hit 'quick brown fox' with a default UnifiedHighlighter built on that same searcher. You expect 'quick brown &lt;b&gt;fox&lt;/b&gt;', which the highlighter returns with WEIGHT_MATCHES off and the legacy Highlighter and FastVectorHighlighter also give. Instead the snippet has no mark, because FieldOffsetStrategy rewrites the query again on the hit's own segment or a one-document view of it, where 'the' counts as rare and becomes required (+the +fox), so the hit no longer matches; the result changes with segment layout alone, and MoreLikeThisQuery with its default minDocFreq rewrites there to an empty query, so its hits are never highlighted.

## Expected vs actual

- Expected: You expect 'quick brown &lt;b&gt;fox&lt;/b&gt;', which the highlighter returns with WEIGHT_MATCHES off and the legacy Highlighter and FastVectorHighlighter also give.
- Actual: The snippet has no mark, because FieldOffsetStrategy rewrites the query again on the hit's own segment or a one-document view of it, where 'the' counts as rare and becomes required (+the +fox), so the hit no longer matches; the result changes with segment layout alone, and MoreLikeThisQuery with its default minDocFreq rewrites there to an empty query, so its hits are never highlighted.

## Files

- [src/org/apache/lucene/queries/TestUnifiedHighlighterCommonTermsRewrite.java](src/org/apache/lucene/queries/TestUnifiedHighlighterCommonTermsRewrite.java) (JUnit test)
- [src/org/apache/lucene/queries/TestUnifiedHighlighterMoreLikeThisRewrite.java](src/org/apache/lucene/queries/TestUnifiedHighlighterMoreLikeThisRewrite.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.TestUnifiedHighlighterCommonTermsRewrite

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.TestUnifiedHighlighterMoreLikeThisRewrite
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
