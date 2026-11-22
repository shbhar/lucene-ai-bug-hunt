# SpanNearQuery returns no match in a segment in which its own field has no indexed terms, so a SpanNearQuery whose clauses are wrapped in FieldMaskingSpanQuery can miss a document that every clause matches, and its hits can change with a merge (5.2.0 to 10.6.0, branch_11x and main; the FieldMaskingSpanQuery javadoc's own example is not affected)

Finding LFV-1547 · Area: queries / span queries (SpanNearQuery: the per-segment check of the query's own field in SpanNearWeight.getSpans, met through FieldMaskingSpanQuery clauses) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index two documents that both contain first:james and last:jones, only the first of which also has a field id, each in a segment of its own, and search with a SpanNearQuery over the two terms, each wrapped in FieldMaskingSpanQuery with the masking field id (slop -1 and unordered, the values of the FieldMaskingSpanQuery javadoc's example, in which only one clause is masked). You expect both documents, because that javadoc says 'the masked SpanQuery will function as normal, but SpanQuery#getField() simply hands back the value supplied in this class's constructor'. Instead IndexSearcher.count returns 1, with no error, and returns 2 once forceMerge(1) has put the two documents into one segment. SpanNearQuery returns nothing for a segment in which its own field, for masked clauses the masking field, has no indexed terms (LeafReader.terms(field) is null), before it asks any clause for its spans, so a document without that field is found only while some document of its segment, live or deleted, has an indexed term in it (a stored, doc-values or point field of that name did not count in the runs): the hits changed with a merge in both directions in the runs, and a hit can be lost only where every clause has spans in such a segment (by reading the code and by a model of 27 query shapes).

## Expected vs actual

- Expected: You expect both documents, because that javadoc says 'the masked SpanQuery will function as normal, but SpanQuery#getField() simply hands back the value supplied in this class's constructor'.
- Actual: IndexSearcher.count returns 1, with no error, and returns 2 once forceMerge(1) has put the two documents into one segment.

## Files

- [src/org/apache/lucene/queries/spans/TestSpanNearMaskedClausesAcrossSegments.java](src/org/apache/lucene/queries/spans/TestSpanNearMaskedClausesAcrossSegments.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): still reproduces.
- Tested on Lucene main ec3b3cca8f10 (a commit of 2026-11-18): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.spans.TestSpanNearMaskedClausesAcrossSegments
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
