# PerFieldSimilarityWrapper does not forward Similarity#computeQueryTermWeight, so a BM25Similarity or StableTflSimilarity k3 (query-term saturation) setting silently has no effect behind a per-field wrapper

Bug LFV-259 · Area: search / similarities (PerFieldSimilarityWrapper, Similarity#computeQueryTermWeight) and BooleanQuery duplicate-clause rewrite · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure BM25Similarity with its opt-in k3 parameter (new BM25Similarity(1.2f, 0.75f, true, 7f)), whose javadoc promises that a term repeated in a query is weighted ((k3 + 1) * qtf) / (k3 + qtf) instead of linearly, and you install it through a PerFieldSimilarityWrapper, the documented way to use per-field similarities, even one whose get returns that same instance for every field. You expect the query 'apple apple' to score as it does with the bare BM25Similarity, where BooleanQuery's duplicate-clause rewrite gives (body:apple)^1.7777778. Instead the rewrite asks the wrapper, which does not forward Similarity#computeQueryTermWeight and inherits its linear default, so the query becomes (body:apple)^2.0 and the document scores 0.63013375 instead of 0.5601189; StableTflSimilarity's k3 is ignored the same way.

The test shows that `BM25Similarity(1.2, 0.75, true, 7)` behind a `PerFieldSimilarityWrapper` that returns it for every field scores the query "apple apple" differently from the bare similarity, because the wrapper does not forward `computeQueryTermWeight` (k3 is ignored).

## Expected vs actual

Expected: the bare BM25 score 0.23247036 (duplicate-clause boost 1.7777778); actual (-ea and -da): 0.26152915 (linear boost 2.0).

## Files

- [src/org/apache/lucene/search/TestPerFieldWrapperK3.java](src/org/apache/lucene/search/TestPerFieldWrapperK3.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestPerFieldWrapperK3
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
