# DocAndScoreQuery.createDocAndScoreQuery (public on main since #16686) keeps repeated doc ids: a document listed twice is counted and returned twice, and a repeated first document of a segment is iterated by the previous segment as doc id == maxDoc (a deleted or MUST_NOT-excluded document comes back, or a search with deletions or a doc-values sort throws)

Bug LFV-955 · Area: search / kNN query execution (DocAndScoreQuery public factory, new in 11.0 via #16686; Weight#count and DocIdSetIterator contracts) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a query with the public DocAndScoreQuery.createDocAndScoreQuery from a TopDocs that lists some document twice, for example the concatenated, score-sorted results of two KnnFloatVectorQuery searches that both found it, and you expect what the javadoc promises, a query that 'matches exactly the documents in topK', so each document is counted and returned once. Instead every entry is kept: for 4 documents found through both fields IndexSearcher#count and totalHits are 8, each hit comes back twice and the leaf iterator returns 1 1 2 2 3 3 4 4. When the repeated document is the first document of a segment (docs [64, 64, 65] with segment 1 starting at 64), Arrays.binarySearch in findSegmentStarts can land on the later copy, so segment 0's iterator returns doc 64, which equals its maxDoc: a plain search then throws ArrayIndexOutOfBoundsException from the live docs when segment 0 has deletions, a doc-values sort throws or sorts the document by another document's value, and when segment 0 has no deletions the document is returned even if it is deleted or excluded by a MUST_NOT clause.

The test shows that the public `DocAndScoreQuery.createDocAndScoreQuery` keeps a repeated doc id, and when it is the first document of a segment the previous segment's iterator returns a doc id equal to that segment's maxDoc.

## Expected vs actual

Expected segment 0 (doc 0 only) to match nothing for topK = [1, 1, 2]; actual: its iterator returns doc 1 (`expected:<2147483647> but was:<1>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/TestDocAndScoreQueryRepeatedDoc.java](src/org/apache/lucene/search/TestDocAndScoreQueryRepeatedDoc.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestDocAndScoreQueryRepeatedDoc
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/docandscorequery-public-factory-count-and-iterator-contracts/](../../models/docandscorequery-public-factory-count-and-iterator-contracts/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
