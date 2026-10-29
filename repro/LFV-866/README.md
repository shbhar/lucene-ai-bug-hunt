# Word2VecSynonymFilterFactory accepts any positive maxSynonymsPerTerm, but Word2VecSynonymProvider sizes its HNSW search as maxSynonymsPerTerm + 1 with no clamp to the model size, so very large values (Integer.MAX_VALUE, where the + 1 overflows) make every in-model token throw IllegalArgumentException or OutOfMemoryError

Bug LFV-866 · Area: analysis-common / synonym.word2vec (Word2VecSynonymProvider.getSynonyms search size; Word2VecSynonymFilterFactory validation) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure Word2VecSynonymFilterFactory with maxSynonymsPerTerm=2147483647 (Integer.MAX_VALUE), which its validation accepts ('must be a positive integer greater than 0'), to keep every synonym above minAcceptedSimilarity, and you expect those synonyms or a rejection at configuration time. The analyzer builds, but Word2VecSynonymProvider.getSynonyms passes maxSynonymsPerTerm + 1 (int arithmetic) as topK to HnswGraphSearcher.search, which wraps to Integer.MIN_VALUE, so the first token with a model vector throws IllegalArgumentException 'initialCapacity must be &gt; 0 and &lt; 2147483630; got: -2147483648' from TernaryLongHeap (on 9.7.0 to 10.3.2: 'maxSize must be &gt; 0...' from LongHeap). Because the search size is never clamped to the model size and the search allocates its queues of topK entries up front, values close to Integer.MAX_VALUE fail the same way and values from roughly heap-bytes/16 up (1e8 at a 1 GiB heap) throw OutOfMemoryError, even for a 3-term model.

The test shows that `Word2VecSynonymFilterFactory` accepts `maxSynonymsPerTerm=Integer.MAX_VALUE`, but the first token that has a model vector then fails, because the HNSW search size `maxSynonymsPerTerm + 1` overflows and is never clamped to the model size.

## Expected vs actual

Expected "alpha" to analyse to [alpha, beta] (or a rejection at configuration time); actual: `IllegalArgumentException: initialCapacity must be > 0 and < 2147483630; got: -2147483648` from `TernaryLongHeap`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/synonym/word2vec/TestWord2VecLargeMaxSynonymsPerTerm.java](src/org/apache/lucene/analysis/synonym/word2vec/TestWord2VecLargeMaxSynonymsPerTerm.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.synonym.word2vec.TestWord2VecLargeMaxSynonymsPerTerm
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
