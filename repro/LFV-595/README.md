# MatchHighlighter.highlight(topDocs, q1, q2,...) silently drops the highlights of every field that only the second or a later query matches: each document keeps only the stored field values of the first query's MatchRegionRetriever, which loads just the fields that first query affects (regression in 10.0.0)

Bug LFV-595 · Area: highlighter / matchhighlight: MatchHighlighter multi-query highlighting (DocHit field values on top of MatchRegionRetriever's per-query stored-field loading) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-595.html](../../bugs/LFV-595.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You pass several queries to MatchHighlighter.highlight(topDocs, q1, q2), for example title:foo and body:bar for a document whose title is 'foo' and whose body is 'bar'; the class's own test passes the sub-clauses of the search query this way to give each query its own markers. You expect both fields to be highlighted, exactly as highlight(topDocs, title:foo OR body:bar) returns them: {title=[&gt;foo&lt;], body=[&gt;bar&lt;]}. Instead you get only {title=[&gt;foo&lt;]} (only {body=[&gt;bar&lt;]} with the queries swapped), with no error, because each document keeps only the stored fields that the first query's retriever loaded; 9.x returned both fields.

## Expected vs actual

- Expected: `highlight(topDocs, title:foo, body:bar)` returns doc 0 `{body=[>bar<], title=[>foo<]}` and doc 1 `{body=[>bar<]}`, as the combined query does.
- Actual: It returns doc 0 `{title=[>foo<]}` and doc 1 `{}`; with the queries swapped, doc 0 `{body=[>bar<]}` and doc 1 `{body=[>bar<]}`.

## Files

- [src/org/apache/lucene/search/matchhighlight/TestMatchHighlighterMultiQueryFields.java](src/org/apache/lucene/search/matchhighlight/TestMatchHighlighterMultiQueryFields.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.matchhighlight.TestMatchHighlighterMultiQueryFields
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestMatchHighlighterMultiQueryFields.java](TestMatchHighlighterMultiQueryFields.java)
- [2-TestMatchHighlighterMultiQueryFields.java](2-TestMatchHighlighterMultiQueryFields.java)
- [TestMultiQueryCrossFieldHighlights.java](TestMultiQueryCrossFieldHighlights.java)
- [TestMultiQueryCrossFieldControls.java](TestMultiQueryCrossFieldControls.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
