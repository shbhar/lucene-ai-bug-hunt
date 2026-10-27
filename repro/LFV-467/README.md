# FloatVectorSimilarityQuery and ByteVectorSimilarityQuery with traversalSimilarity == resultSimilarity (the 3- and 4-argument constructors: the plain ones in 9.10.0-10.4.0, the deprecated Explicit classes in 10.5.2 and main) silently drop documents whose similarity is exactly resultSimilarity, because the collector's minCompetitiveSimilarity rises to the inclusive threshold and every searcher treats an equal score as not competitive

Bug LFV-467 · Area: search / vector similarity-threshold queries (ExplicitVectorSimilarityCollector with the exhaustive scan and the HNSW graph search) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-467.html](../../bugs/LFV-467.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index vectors with the default codec and run FloatVectorSimilarityQuery.Explicit(field, target, 1.0f) on main or 10.5.2 (the plain FloatVectorSimilarityQuery(field, target, 1.0f) on 9.10.0-10.4.0), which sets traversalSimilarity = resultSimilarity, to find every exact copy of target under EUCLIDEAN, and you expect every document with similarity &gt;= 1.0, as collect ('similarity &gt;= resultSimilarity'), explain ('Score above threshold') and the query's own exact fallback all count them. Instead only the first copy comes back: a 65-vector segment with copies at doc 0 and doc 64 returns [0] and count 1, the same query with a MatchAllDocsQuery filter returns [0, 64], a 4,000-vector HNSW segment returns 1 of its 12 copies, and a traversalSimilarity one ulp lower returns all of them (byte vectors at an exact 0.5 behave the same). Once a hit at the threshold is collected, ExplicitVectorSimilarityCollector.minCompetitiveSimilarity = min(traversalSimilarity, maxSimilarity) equals the inclusive result threshold, and both the exhaustive scan (a 64-vector batch is collected only if its best score &gt; minCompetitiveSimilarity) and the graph search (score &gt;= nextUp(minCompetitiveSimilarity)) treat an equal score as not competitive; the one-line fix returns min(nextDown(traversalSimilarity), maxSimilarity).

## Expected vs actual

- Expected: `[0, 64]` and `count` 2 on the 65-vector segment; all 6 copies on the graph segment.
- Actual: `[0]` and `count` 1; `[7]`, 1 of 6 copies, on the graph segment; `explain` reports every copy as a match.

## Files

- [src/org/apache/lucene/search/TestExplicitSimilarityThresholdTie.java](src/org/apache/lucene/search/TestExplicitSimilarityThresholdTie.java) (JUnit test)
- [src2/org/apache/lucene/search/TestExplicitSimilarityThresholdTie.java](src2/org/apache/lucene/search/TestExplicitSimilarityThresholdTie.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestExplicitSimilarityThresholdTie
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestExplicitSimilarityThresholdTie
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestExplicitSimilarityThresholdTie.java](TestExplicitSimilarityThresholdTie.java)
- [fix-nextdown-traversal.diff](fix-nextdown-traversal.diff)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
