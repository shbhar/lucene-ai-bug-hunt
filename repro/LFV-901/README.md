# IndexWriter.deleteDocuments with a kNN query runs on the writer's pooled segment reader, whose live docs miss the writer's own earlier deletes, and on the whole flushed segment including documents added later, so the query picks its k nearest among documents already deleted or added after the call and the acknowledged delete removes fewer documents than it should, often none (since 9.0.0)

Bug LFV-901 · Area: core / index write path (delete-by-query: FrozenBufferedUpdates.applyQueryDeletes over the ReadersAndUpdates pooled reader) x core / search kNN queries · Priority: medium · Impact: data corruption · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 10 documents with vectors, delete the nearest one (d0) by its id term, commit, then call deleteDocuments(new KnnFloatVectorQuery(field, target, 1)) and expect it to delete the nearest remaining document, d1, which is what an NRT search at that point returns. Instead nothing more is deleted: IndexWriter runs delete queries on its internal pooled SegmentReader, whose live docs are not refreshed after deletes, so the kNN query still sees d0 as live and picks it again (also after an NRT reopen, or with both deletes in one session). Likewise, while the documents are still buffered, a document added after the kNN delete can take the top slot inside the flushed segment and is then cut by the delete's docID limit, so the delete removes nothing.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Nothing more is deleted: IndexWriter runs delete queries on its internal pooled SegmentReader, whose live docs are not refreshed after deletes, so the kNN query still sees d0 as live and picks it again (also after an NRT reopen, or with both deletes in one session).

## Files

- [src/org/apache/lucene/index/TestKnnDeleteQueryLiveDocsView.java](src/org/apache/lucene/index/TestKnnDeleteQueryLiveDocsView.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestKnnDeleteQueryLiveDocsView
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
