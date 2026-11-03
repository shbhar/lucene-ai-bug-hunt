# kNN vector query rewrite throws (AssertionError with -ea, IllegalArgumentException 'initialCapacity must be &gt; 0' with -da) instead of matching nothing when a leaf's parent reader has maxDoc 0, because the per-leaf share 0/0 is NaN and the per-leaf top-k becomes 0 (regression in 10.3.0)

Finding LFV-1209 · Area: search / kNN vector queries (AbstractKnnVectorQuery optimistic per-leaf top-k) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1209.html](../../bugs/LFV-1209.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a reader that holds an empty leaf under an empty parent, such as new MultiReader(new ParallelLeafReader()), wrap it in an executor-backed IndexSearcher and rewrite a KnnFloatVectorQuery("v", {1, 0}, 3) on it, expecting MatchNoDocsQuery: that is what the rewrite returns when nothing matches, and what 10.2.2 returns for the same input. Instead the optimistic per-leaf collector computes the leaf's share of its parent as 0 / 0f = NaN; Math.max(1, NaN) stays NaN and the int cast gives a per-leaf top-k of 0, so the rewrite fails at 'assert perLeafTopK &gt; 0' with -ea, and with -da the heap behind the collector throws IllegalArgumentException 'initialCapacity must be &gt; 0 and &lt; 2147483630; got: 0'.

## Expected vs actual

- Expected: The rewrite returns `MatchNoDocsQuery` ("No documents matched the nearest-neighbor search").
- Actual: `AssertionError` at `AbstractKnnVectorQuery.java:329` with assertions on; with them off, `IllegalArgumentException: initialCapacity must be > 0 and < 2147483630; got: 0` at `TernaryLongHeap.<init>`.

## Files

- [src/org/apache/lucene/search/TestKnnRewriteEmptyParent.java](src/org/apache/lucene/search/TestKnnRewriteEmptyParent.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestKnnRewriteEmptyParent
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestKnnRewriteEmptyParent.java](TestKnnRewriteEmptyParent.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
