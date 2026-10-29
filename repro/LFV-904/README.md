# IndexWriter.deleteDocuments(Query) and updateDocuments(Query,...) rewrite the query separately on each segment, so reader-dependent queries (kNN, the default FuzzyQuery and other top-N term rewrites, CommonTermsQuery, MoreLikeThisQuery, score ranges) delete a set that depends on the segment layout and differs from what IndexSearcher.search returns

Bug LFV-904 · Area: core / index write path (delete-by-query: FrozenBufferedUpdates.applyQueryDeletes) x core / search and queries (reader-dependent rewrites) · Priority: medium · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-904.html](../../bugs/LFV-904.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call IndexWriter.deleteDocuments(query) (or updateDocuments(query, docs)) on an index with two or more segments and expect it to delete the documents IndexSearcher.search(query) returns, since the javadoc says it 'Deletes the document(s) matching any of the provided queries'. Instead the writer rewrites the query on a separate one-segment IndexSearcher for each segment, so top-k, top-N term expansion and index statistics are computed per segment: a kNN query with k=2 deletes up to 2 documents per segment (4 on two segments), a default FuzzyQuery deletes 60 documents where search finds 50, and MoreLikeThisQuery or a score-range predicate deletes none of the documents search returns. With the same documents committed in one segment before the delete, the delete equals the search result, so the segment layout alone decides what is deleted, and the result is permanent once committed.

The test shows that `IndexWriter.deleteDocuments(Query)` resolves a kNN query on each segment separately, so on two segments it deletes a different set than `IndexSearcher` returns for the same query.

## Expected vs actual

Expected: `KnnFloatVectorQuery` k=1 matches 1 of the 2 documents (one per segment), so 1 document stays live; actual: the top 1 of each segment is deleted, 0 left (`expected:<1> but was:<0>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/index/TestDeleteByKnnQuery.java](src/org/apache/lucene/index/TestDeleteByKnnQuery.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestDeleteByKnnQuery
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
