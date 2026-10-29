# Normalization.NoNormalization.explain reports 1 instead of tf, so IBSimilarity explains its Distribution at tfn = 1 and that child disagrees with the score it explains

Bug LFV-921 · Area: core / search similarities (Normalization.NoNormalization explanation; IBSimilarity and DFRSimilarity explain trees) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You score a field with IBSimilarity(distribution, lambda, new Normalization.NoNormalization) and call IndexSearcher.explain on a document where the term occurs 3 times. You expect the explanation to add up: the IB node says it is 'computed as boost * distribution.score(stats, normalization.tfn(stats, freq, docLen), lambda.lambda(stats))', NoNormalization.tfn returns tf, and Normalization.explain is documented to return 'an explanation for the normalized term frequency', so the normalization child should show 3 and the Distribution child should equal the node at boost 1. Instead NoNormalization.explain returns the value 1, IBSimilarity feeds that 1 into distribution.explain as tfn, and the Distribution child shows 0.9162907 under a node of 1.7047480 (all four IB distribution/lambda pairs), while the score itself is right.

The test shows that with IBSimilarity(DistributionLL, LambdaDF, NoNormalization) the explanation's Distribution child is computed at tfn = 1 (NoNormalization.explain reports 1, not tf), so it disagrees with the IB score node it explains.

## Expected vs actual

Expected the Distribution child to equal the node at boost 1 (1.3862944, tfn = freq = 3); actual: 0.6931472 (`expected:<1.3862943649291992> but was:<0.6931471824645996>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/similarities/TestIBNoNormalizationExplain.java](src/org/apache/lucene/search/similarities/TestIBNoNormalizationExplain.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.similarities.TestIBNoNormalizationExplain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/explanation-formula-strings-vs-their-own-operand-values/](../../models/explanation-formula-strings-vs-their-own-operand-values/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
