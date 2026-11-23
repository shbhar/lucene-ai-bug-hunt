# kNN and vector-similarity searches that hit the IndexSearcher timeout before collecting any hit return 0 hits with IndexSearcher.timedOut() == false, so the timed-out search looks like a search without matches; a vector-similarity search on several segments can also return only the first segments' hits with timedOut() == false

Finding LFV-115 · Area: search / IndexSearcher timeout reporting for vector queries (AbstractKnnVectorQuery rewrite, AbstractVectorSimilarityQuery scorerSupplier) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You set a timeout with IndexSearcher#setTimeout and run a kNN query (KnnFloatVectorQuery or KnnByteVectorQuery) or an approximate FloatVectorSimilarityQuery/ByteVectorSimilarityQuery, and the timeout fires before the vector search has found its first hit. You expect what a TermQuery gives under the same timeout: 0 hits with IndexSearcher#timedOut() == true, the documented signal ('Returns true if any search hit the timeout') that the answer is incomplete. Instead the search returns 0 hits, TotalHits '0 hits' (EQUAL_TO) and timedOut() == false, so the timed-out search cannot be told apart from a search that has no matches; IndexSearcher#count of a kNN query behaves the same way.

## Expected vs actual

- Expected: You expect what a TermQuery gives under the same timeout: 0 hits with IndexSearcher#timedOut() == true, the documented signal ('Returns true if any search hit the timeout') that the answer is incomplete.
- Actual: The search returns 0 hits, TotalHits '0 hits' (EQUAL_TO) and timedOut() == false, so the timed-out search cannot be told apart from a search that has no matches; IndexSearcher#count of a kNN query behaves the same way.

## Files

- [src/org/apache/lucene/search/TestVectorQueryTimeoutReporting.java](src/org/apache/lucene/search/TestVectorQueryTimeoutReporting.java) (JUnit test)
- [src2/org/apache/lucene/search/TestVectorQueryTimeoutReporting.java](src2/org/apache/lucene/search/TestVectorQueryTimeoutReporting.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): still reproduces.
- Tested on Lucene main ea57360912ed (a commit of 2026-10-24): reproduced.
- This folder holds the registered repro, which shows the first form in the title, 0 hits with timedOut() == false: three tests, each on an index of 100 documents with a QueryTimeout that returns true at every check; the kNN test and the similarity test fail, and the term-query control passes. src/ and src2/ hold the same three tests: src/ is for 10.5.2 and later, and src2/ is for the releases before 10.5.2, where the similarity query is written new FloatVectorSimilarityQuery(...); on 9.11.1 and 9.12.3 only the kNN test of src2 fails, because the similarity queries are affected from 10.0.0 on; on main 781025d6fe32 the src2 copy does not compile (the class is abstract there) and its java command ends in a ClassNotFoundException, which is not the finding, so leave out the javac line and the java command of src2. The test of the second form in the title, a vector-similarity search on several segments that returns only the first segments' hits with timedOut() == false, is not shipped here: it has two segments of one matching document each, a FloatVectorSimilarityQuery and a QueryTimeout that returns true once the first hit has been collected, and it gets 1 of the 2 matching documents with timedOut() == false (the finding's Affected versions text, in the table of all findings, says where it was run).
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestVectorQueryTimeoutReporting
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestVectorQueryTimeoutReporting
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
