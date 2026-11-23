# On the 11.0 branch and main (both unreleased), a kNN vector query without a filter can return no hits on a soft-deletes reader (SoftDeletesDirectoryReaderWrapper, whose live docs are a bit set) when fewer than 60% of a segment is live and the deleted documents fill the neighbourhood of the query vector: live docs alone select the filter-optimized HNSW search, and the query's no-filter branch lacks the exact-search fallback of its filter branch (10.6.0 returns the hit by default and gives the same empty result only with KnnSearchStrategy.Hnsw(60))

Finding LFV-1518 · Area: search / kNN vector queries (AbstractKnnVectorQuery, AcceptDocs) with the filter-optimized HNSW searcher (util.hnsw), on a soft-deletes reader (SoftDeletesDirectoryReaderWrapper) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You keep soft-deleted documents in the index (a retention merge policy, or no merge yet), read it through SoftDeletesDirectoryReaderWrapper and run KnnFloatVectorQuery, without a filter, for the 1 nearest neighbour of a vector whose 500 nearest documents, half of a 1,000-document segment, are soft-deleted. You expect one hit, a live document, as Lucene 10.6.0 returns and as the same build returns when the same documents are hard-deleted or excluded by a filter query. On the 11.0 branch and main the query returns no hit, silently, with assertions on or off: the wrapper's live docs are a bit set, so the query's accept set reports 500 of 1,000 documents, which is under the 60% at which the default strategy of 11.0 switches to the filter-optimized HNSW search; that search does not make a deleted neighbour a candidate and crosses deleted nodes only within a bounded reach, here it scores no live node, and the query's no-filter branch returns the empty result where its filter branch would run an exact search.

## Expected vs actual

- Expected: You expect one hit, a live document, as Lucene 10.6.0 returns and as the same build returns when the same documents are hard-deleted or excluded by a filter query.
- Actual: While the reported behaviour is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/search/TestKnnQueryOnSoftDeletesReader.java](src/org/apache/lucene/search/TestKnnQueryOnSoftDeletesReader.java) (JUnit test)

## Run it

- Tested on Lucene main 9965d34e3c77 (a commit of 2026-11-16): reproduced, with assertions on and off.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced, with assertions on and off.
- Tested on Lucene branch_11x daa54f2e0cd3 (a commit of 2026-11-13): reproduced, with assertions on and off.
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): the test passes, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0): the release's default strategy returns the hit.
- What each build does: the test fails on Lucene main and on the 11.0 release branch (branch_11x daa54f2e0cd3), both unreleased, with and without assertions, and passes with the 10.6.0 release jars. Lucene 10.2.0 to 10.6.0 return the same empty result only when the query is given KnnSearchStrategy.Hnsw with a threshold above the live share (run with Hnsw(60)); with KnnSearchStrategy.Hnsw(0) on the query, main and branch_11x return the hit. What the test does: it writes 1,000 documents with the one-dimensional vectors (0) to (999) into one segment, soft-deletes the 500 nearest to (0), opens the index through SoftDeletesDirectoryReaderWrapper and asks KnnFloatVectorQuery, without a filter, for the 1 nearest neighbour of (0). It expects 1 hit and fails with 'hits of a kNN query for 1 neighbour among 500 live documents expected:&lt;1&gt; but was:&lt;0&gt;'. Why its settings: NoMergePolicy keeps the half-deleted segment, which the default merge policy rewrites when the writer closes (in a probe on main and branch_11x a retention merge policy over the default one kept it as well, with the same empty result); the codec is set to the default codec because the test framework otherwise draws one at random; the segment has 1,000 documents because the default codec writes no graph for a small one (in a probe on main the hit came back for 500 to 640 documents and not for 660 to 1,000). The input has no randomness. On a checkout of the 11.0 branch set V=11.0.0-SNAPSHOT; for the release, put lucene-core, lucene-codecs and lucene-test-framework 10.6.0 from Maven Central (org.apache.lucene) in place of the three $LUCENE jars. For the Gradle alternative at the end of this page the module is lucene/core.
- This finding was re-run on Lucene main b5a4546a5964 on 2026-11-22: still reproduces.
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

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestKnnQueryOnSoftDeletesReader
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
