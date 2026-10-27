# UnifiedHighlighter and MatchRegionRetriever throw IllegalArgumentException on a MultiReader whose indexes differ in any field's schema, even only in norms or in an unrelated field, because FieldInfos.getMergedFieldInfos applies the single-index consistency check across the indexes

Bug LFV-602 · Area: highlighter / uhighlight + matchhighlight: default offset-source choice on composite readers (core index FieldInfos.getMergedFieldInfos) · Priority: medium · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build two indexes in which a field is declared slightly differently, for example 'body' with norms in one and without norms in the other (both with offsets), open them together as new MultiReader(r1, r2), search body:alpha and get both hits. You expect UnifiedHighlighter.highlight("body", query, topDocs) to return both snippets ('&lt;b&gt;alpha&lt;/b&gt; beta', '&lt;b&gt;alpha&lt;/b&gt; gamma'), as it does when both indexes use the same options or when each index is highlighted on its own. Instead it throws IllegalArgumentException 'cannot change field "body" from omitNorms=false to inconsistent omitNorms=true' (and 'cannot change field "tag"...' when only an unrelated field differs), because the default offset-source choice builds FieldInfos.getMergedFieldInfos over the whole MultiReader, which applies the per-index field-consistency check across the two indexes; MatchRegionRetriever's default strategies fail the same way.

## Expected vs actual

- Expected: You expect UnifiedHighlighter.highlight("body", query, topDocs) to return both snippets ('&lt;b&gt;alpha&lt;/b&gt; beta', '&lt;b&gt;alpha&lt;/b&gt; gamma'), as it does when both indexes use the same options or when each index is highlighted on its own.
- Actual: It throws IllegalArgumentException 'cannot change field "body" from omitNorms=false to inconsistent omitNorms=true' (and 'cannot change field "tag"...' when only an unrelated field differs), because the default offset-source choice builds FieldInfos.getMergedFieldInfos over the whole MultiReader, which applies the per-index field-consistency check across the two indexes; MatchRegionRetriever's default strategies fail the same way.

## Files

- [src/org/apache/lucene/search/uhighlight/TestHighlightMultiReaderMixedFieldOptions.java](src/org/apache/lucene/search/uhighlight/TestHighlightMultiReaderMixedFieldOptions.java) (JUnit test)
- [src/repro/HighlightMultiReaderMixedOptionsMain.java](src/repro/HighlightMultiReaderMixedOptionsMain.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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
     execute --select-class org.apache.lucene.search.uhighlight.TestHighlightMultiReaderMixedFieldOptions

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" repro.HighlightMultiReaderMixedOptionsMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
