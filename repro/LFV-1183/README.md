# A kNN query whose filter iterates a BitSetIterator over a bit set shorter than the segment's maxDoc throws ArrayIndexOutOfBoundsException (AssertionError with -ea) instead of returning the filtered hits, because AcceptDocs reuses that set as the accept bits without checking its length (since 9.3.0)

Finding LFV-1183 · Area: core / search: filtered kNN accept docs (AcceptDocs.createBitSet behind KnnFloatVectorQuery/KnnByteVectorQuery filters, read by Lucene99HnswVectorsReader and HnswGraphSearcher) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1183.html](../../bugs/LFV-1183.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a KnnFloatVectorQuery with a custom filter whose scorer iterates a BitSetIterator over a FixedBitSet sized to its own matches (10 bits for docs 0..9) on a 100-document segment with no deletions, where every document has a vector. You expect the top k vectors among the filter's matches (here doc 9, with k = 1), as with the same filter backed by a maxDoc-sized set. Instead AcceptDocs.createBitSet hands the filter's 10-bit set unchanged to the HNSW reader as the accept bits (its other branches build maxDoc bits, and its fromLiveDocs entry point rejects bits whose length is not maxDoc), the reader tests it for every vector ordinal, and the search throws ArrayIndexOutOfBoundsException (AssertionError 'index=10, numBits=10' with -ea) from FixedBitSet.get.

## Expected vs actual

- Expected: `search` returns doc 9, the nearest of the 10 filtered documents.
- Actual: `search` throws `ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1` (assertions off) or `AssertionError: index=10, numBits=10` (on) from `FixedBitSet.get`.

## Files

- [src/org/apache/lucene/search/TestKnnFilterBitSetShorterThanMaxDoc.java](src/org/apache/lucene/search/TestKnnFilterBitSetShorterThanMaxDoc.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestKnnFilterBitSetShorterThanMaxDoc
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestKnnFilterBitSetShorterThanMaxDoc.java](TestKnnFilterBitSetShorterThanMaxDoc.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
