# QueryBitSetProducer caches each segment's bit set under the segment core key without asking Weight#isCacheable, so a reused ToParentBlockJoinSortField child filter keeps its old child selection after an in-place doc-values update and a reopen that shares the core: parents sort 'B A' instead of 'A B', with no error; since 5.3.0

Finding LFV-1394 · Area: join / QueryBitSetProducer per-segment cache (the childFilter of ToParentBlockJoinSortField) and the SegmentCacheable contract · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1394.html](../../bugs/LFV-1394.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You sort parent documents with ToParentBlockJoinSortField by the lowest price among in-stock children, using new QueryBitSetProducer(NumericDocValuesField.newSlowExactQuery("stock", 1)) as the child filter, then put one child back in stock with IndexWriter#updateNumericDocValue, commit and reopen with DirectoryReader.openIfChanged. You expect the reopened searcher to sort by the new selection ('A B': A's cheapest in-stock child now costs 1, B's costs 5), because the child filter defines which children take part and a query that reads updated doc values is not cacheable (SegmentCacheable). Instead QueryBitSetProducer returns the bit set it cached for the old reader under the segment core key, which an in-place doc-values update does not change, and never asks the Weight whether the result may be cached, so the parents still sort 'B A'; a fresh producer or a newly opened reader gives 'A B'.

## Expected vs actual

- Expected: `getBitSet` on the reopened leaf sets doc 0, which now has `stock` = 1.
- Actual: No match (`assertTrue` at line 50 fails): the producer returns the result it cached for the old reader.

## Files

- [src/org/apache/lucene/search/join/TestQueryBitSetProducerDocValuesUpdate.java](src/org/apache/lucene/search/join/TestQueryBitSetProducerDocValuesUpdate.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/join/build/libs/lucene-join-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestQueryBitSetProducerDocValuesUpdate
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestQueryBitSetProducerDocValuesUpdate.java](TestQueryBitSetProducerDocValuesUpdate.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
