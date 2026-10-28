# Word2VecSynonymProvider returns maxSynonymsPerTerm + 1 synonyms when the query term is not among its own k+1 HNSW hits (tied vectors or an approximate-search miss), so Word2VecSynonymFilter emits one synonym more than the documented maximum

Bug LFV-862 · Area: analysis-common / synonym.word2vec (Word2VecSynonymProvider.getSynonyms result cap; Word2VecSynonymFilter) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-862.html](../../bugs/LFV-862.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure Word2VecSynonymFilter with maxSynonymsPerTerm=k, whose javadoc calls it the 'maximum number of result returned by the synonym search', so you expect at most k synonyms per token. Word2VecSynonymProvider.getSynonyms searches the HNSW graph for k+1 neighbours on the assumption that the query term is always its own nearest neighbour, drops only the query term and never caps the result, so when the term is not among the hits it returns all k+1. With three terms that share one vector and k=1, getSynonyms(c) returns [a, b] and the filter emits 2 SYNONYM tokens; with 4000 distinct clustered vectors and the factory defaults, 4 terms get 6 synonyms because the approximate search with a beam of 6 does not reach the query term itself, although it is its own strict nearest neighbour by a margin of more than 0.03.

## Expected vs actual

- Expected: At most 1 synonym for each of `a`, `b` and `c`, and at most 5 for every word of the 500-word model.
- Actual: `c` gets `[a, b]` and `w138` gets 6 synonyms: 2 of 2 tests fail, with assertions on and off.

## Files

- [src/org/apache/lucene/analysis/synonym/word2vec/TestWord2VecMaxSynonymsPerTerm.java](src/org/apache/lucene/analysis/synonym/word2vec/TestWord2VecMaxSynonymsPerTerm.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.synonym.word2vec.TestWord2VecMaxSynonymsPerTerm
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestWord2VecSynonymMaxSynonymsPerTerm.java](TestWord2VecSynonymMaxSynonymsPerTerm.java)
- [TestWord2VecMaxSynonymsPerTerm.java](TestWord2VecMaxSynonymsPerTerm.java)
- [TestSynonymCountCap.java](TestSynonymCountCap.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
