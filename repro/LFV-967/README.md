# CompletionWeight returns a non-null ScorerSupplier whose bulkScorer is null, so IndexSearcher#search of a CompletionQuery throws NullPointerException on a segment the query cannot match (regression in 10.0 from #13408)

Bug LFV-967 · Area: suggest / document (NRT) suggester: CompletionWeight#scorerSupplier vs the Weight/ScorerSupplier contract and IndexSearcher#searchLeaf · Priority: low · Impact: crash · Novelty: new

Bug page: [../../bugs/LFV-967.html](../../bugs/LFV-967.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index suggestions with SuggestField and run a PrefixCompletionQuery through IndexSearcher#search with a CollectorManager of TopSuggestDocsCollector, on an index where one segment has no document with the suggest field (or where the query's filter matches no document of a segment). You expect the same suggestions as SuggestIndexSearcher#suggest (3 in the repro), because Weight#scorerSupplier 'must return null if the scorer is null' and IndexSearcher then skips that segment, as 9.12.3 does. Instead CompletionWeight returns a non-null ScorerSupplier whose bulkScorer returns null, and IndexSearcher#searchLeaf throws NullPointerException ('Cannot invoke BulkScorer.score(...) because "scorer" is null'; with a timeout set, Objects.requireNonNull in TimeLimitingBulkScorer throws it first).

The test shows that IndexSearcher#search of a PrefixCompletionQuery (with a TopSuggestDocsCollector manager) over two segments, the second without the suggest field, crashes because CompletionWeight returns a non-null ScorerSupplier whose bulkScorer() is null.

## Expected vs actual

Expected: the one suggestion 'apple' (as SuggestIndexSearcher#suggest and 9.12.3 return; Weight#scorerSupplier must return null when there is no scorer); actual: NullPointerException at IndexSearcher.searchLeaf (-ea and -da).

## Files

- [src/org/apache/lucene/search/suggest/document/TestCompletionQuerySegmentWithoutSuggestField.java](src/org/apache/lucene/search/suggest/document/TestCompletionQuerySegmentWithoutSuggestField.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed 5EED0967
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=5EED0967 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.document.TestCompletionQuerySegmentWithoutSuggestField
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
