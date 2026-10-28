# Classic Highlighter throws IllegalStateException for any query that contains a FieldExistsQuery (or a query that rewrites to one, such as an open-ended LongField range) when the token stream comes from term vectors with positions and offsets (regression in 10.5.0 from GITHUB#15678)

Bug LFV-824 · Area: highlighter / classic Highlighter (QueryScorer, WeightedSpanTermExtractor term-vector path) · Priority: medium · Impact: crash · Novelty: known upstream

Bug page: [../../bugs/LFV-824.html](../../bugs/LFV-824.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You store a text field with term vectors (positions and offsets), search with a query such as +body:fox plus a FieldExistsQuery filter on another field, and highlight the hit with the classic Highlighter from its term vectors; you expect 'the quick brown &lt;B&gt;fox&lt;/B&gt;', which 10.4.0 and earlier return. Since 10.5.0 the call throws IllegalStateException 'FieldExistsQuery requires that the field indexes doc values, norms or vectors, but field 'shadowed_field' exists and indexes neither of these data structures', naming an internal field the user never created. The highlighter rewrites the query against its own one-field reader built from the term vectors, whose field info has no norms or doc values, and since GITHUB#15678 it no longer skips FieldExistsQuery as GITHUB#12088 had made it do.

## Expected vs actual

- Expected: `getBestFragment` returns `the quick brown <B>fox</B>`, as on 10.4.0 and on main over re-analysed text.
- Actual: `IllegalStateException: FieldExistsQuery requires that the field indexes doc values, norms or vectors, but field 'shadowed_field' exists and indexes neither of these data structures`; 2 of 3 tests fail.

## Files

- [src/org/apache/lucene/search/highlight/TestHighlightFieldExistsTermVectors.java](src/org/apache/lucene/search/highlight/TestHighlightFieldExistsTermVectors.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.highlight.TestHighlightFieldExistsTermVectors
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestHighlightFieldExistsTermVectors.java](TestHighlightFieldExistsTermVectors.java)
- [TestFieldExistsTermVectorHighlight.java](TestFieldExistsTermVectorHighlight.java)
- [TestFieldExistsTermVectorVariants.java](TestFieldExistsTermVectorVariants.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
