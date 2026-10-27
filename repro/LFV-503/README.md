# Default UnifiedHighlighter (WEIGHT_MATCHES) rebuilds the query's Weight on its own single-leaf searcher, so JoinUtil global-ordinals joins throw IllegalStateException and ParentChildrenBlockJoinQuery reads its top-level parent id as a segment-local id

Bug LFV-503 · Area: highlighter / UnifiedHighlighter WEIGHT_MATCHES path (FieldOffsetStrategy) with reader-bound join queries · Priority: medium · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search with a query that contains a JoinUtil global-ordinals join, for example +text:shirt +JoinUtil.createJoinQuery("join", text:great, type:to, searcher, scoreMode, null), and highlight the hits with a UnifiedHighlighter built on that same searcher with default flags. You expect the snippet 'red &lt;b&gt;shirt&lt;/b&gt;', which is what the highlighter returns with WEIGHT_MATCHES off. Instead highlight throws IllegalStateException 'Creating the weight against a different index reader than this query has been built for.', because FieldOffsetStrategy re-creates the query's Weight on a new IndexSearcher over the hit's single leaf, which the join's reader check rejects; the same substitute searcher makes ParentChildrenBlockJoinQuery read its top-level parent doc id as a local id of the hit's segment, so a hit in a later segment loses its highlight ('size small' instead of '&lt;b&gt;size&lt;/b&gt; small') or crashes.

## Expected vs actual

- Expected: You expect the snippet 'red &lt;b&gt;shirt&lt;/b&gt;', which is what the highlighter returns with WEIGHT_MATCHES off.
- Actual: highlight throws IllegalStateException 'Creating the weight against a different index reader than this query has been built for.', because FieldOffsetStrategy re-creates the query's Weight on a new IndexSearcher over the hit's single leaf, which the join's reader check rejects; the same substitute searcher makes ParentChildrenBlockJoinQuery read its top-level parent doc id as a local id of the hit's segment, so a hit in a later segment loses its highlight ('size small' instead of '&lt;b&gt;size&lt;/b&gt; small') or crashes.

## Files

- [src/org/apache/lucene/search/join/TestUnifiedHighlighterGlobalOrdinalsJoin.java](src/org/apache/lucene/search/join/TestUnifiedHighlighterGlobalOrdinalsJoin.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/join/build/libs/lucene-join-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestUnifiedHighlighterGlobalOrdinalsJoin
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
