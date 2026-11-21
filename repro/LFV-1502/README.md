# FaissKnnVectorsFormat (sandbox, new in 11.0): a merge adds size() entries of the merged vector values to the Faiss index but copies only the vectors of live documents, so each vector of a deleted or updated document becomes an all-zero entry with doc id 0, and kNN can return the merged segment's first document, with the zero vector's score, in place of real neighbours when fewer than k live vectors score above the zero vector (typical for EUCLIDEAN data around the origin and for DOT_PRODUCT or COSINE neighbours with a negative similarity)

Finding LFV-1502 · Area: sandbox / codecs: FaissKnnVectorsFormat (the Faiss-based vector format: @lucene.experimental, needs the Faiss C library; added by #14178, 11.0.0 would be its first release): the merge path that builds the Faiss index (FaissLibraryNativeImpl.createIndex) · Priority: medium · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1502.html](../../bugs/LFV-1502.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index vectors with FaissKnnVectorsFormat (a sandbox, experimental vector format that is new in 11.0 and needs the Faiss native library), delete or update a document, and a merge runs. You expect a kNN search to return each live document at most once, with the score of its own vector. Instead the merged segment's Faiss index holds one extra all-zero vector for every vector of a deleted or updated document, labelled as the segment's first document, so a search for which fewer than k live vectors score above that zero vector gets the first document back with the zero vector's score (a second time when it is a hit of its own), in place of real neighbours, with no error, until the segment is merged again. That is typical for EUCLIDEAN data around the origin and for DOT_PRODUCT or COSINE neighbours with a negative similarity; once k exceeds the number of live vectors it also happens with positive similarities and with data far from the origin.

## Expected vs actual

- Expected: `b=0.1000 c=0.0588`: the two live documents, each with the score of its own vector.
- Actual: `b=0.5000 b=0.1000`: b twice, first with the score of an all-zero vector, and c missing (`ComparisonFailure` at line 84).

## Files

- [src/org/apache/lucene/sandbox/codecs/faiss/TestFaissMergeAfterDelete.java](src/org/apache/lucene/sandbox/codecs/faiss/TestFaissMergeAfterDelete.java) (JUnit test)

## Run it

- Tested on Lucene main 7b8418faa2ff (a commit of 2026-11-20): still reproduces.
- Tested on Lucene branch_11x daa54f2e0cd3 (a commit of 2026-11-13): reproduced.
- What the test does: with FaissKnnVectorsFormat as the vector format it indexes three documents a, b and c with the EUCLIDEAN vectors (3, 0), (4, 0) and (5, 0), commits, deletes a and calls forceMerge(1). It then asks for the 2 nearest neighbours of (1, 0) and expects the two live documents with the scores of their own vectors, 'b=0.1000 c=0.0588'. The test fails with 'expected:&lt;b=0.[1000 c=0.0588] &gt; but was:&lt;b=0.[5000 b=0.1000] &gt;': b comes back twice, once with the score of an all-zero vector, the same with and without assertions. It uses no randomness. The test needs the Faiss C library that the format itself needs (libfaiss_c, version 1.11.0, with its dependencies; the package documentation of org.apache.lucene.sandbox.codecs.faiss says how to install it). Before the java command, put the directory that holds the library on the library path, with export LD_LIBRARY_PATH=/path/to/faiss/lib, or add -Djava.library.path=/path/to/faiss/lib to the java command. Without the library the test is skipped by the guard it shares with Lucene's own Faiss tests: the launcher then reports 1 test found, 1 skipped and 0 failed, and exits 0. A skip is not a pass and says nothing about the finding. With -Dtests.faiss.run=true added to the java command a missing library is an error (UnsatisfiedLinkError) instead of a skip.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=4 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.sandbox.codecs.faiss.TestFaissMergeAfterDelete
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestFaissMergeAfterDelete.java](TestFaissMergeAfterDelete.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
