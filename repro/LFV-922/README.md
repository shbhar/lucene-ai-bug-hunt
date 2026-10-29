# AfterEffectB.explain shows the raw totalTermFreq and docFreq under children labelled 'F,... + 1' and 'n,... + 1' (and lists tfn twice), so its own formula (F + 1) / (n * (tfn + 1)) on the children it lists does not give the value it shows; the score itself is correct

Bug LFV-922 · Area: core / search similarities (DFR after effect B explanation: AfterEffectB.explain, reached through DFRSimilarity.explain) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search with DFRSimilarity(basic model, new AfterEffectB, normalization) and call IndexSearcher.explain on a hit. The AfterEffectB node says 'computed as (F + 1) / (n * (tfn + 1)) from:' and labels its children 'F, total number of occurrences of term across all documents + 1' and 'n, number of documents containing term + 1', so for a term with totalTermFreq 6 and docFreq 3 you expect F = 7 and n = 4. Instead the children show 6 and 3 (the raw statistics) and tfn is listed twice, so the formula on the listed children gives 0.71897 while the node correctly shows 0.61626 (I(n)B2); the score is right and only the explanation's operands are wrong.

The test shows that `AfterEffectB.explain` (used by `DFRSimilarity.explain`) puts the raw totalTermFreq and docFreq under children labelled "F,... + 1" and "n,... + 1".

## Expected vs actual

Expected, for totalTermFreq 6 and docFreq 3: F = 7 and n = 4. Actual (-ea and -da): 6 and 3 (`expected:<F=[7, n=4]> but was:<F=[6, n=3]>`). The score is not affected.

## Files

- [src/org/apache/lucene/search/similarities/TestAfterEffectBExplain.java](src/org/apache/lucene/search/similarities/TestAfterEffectBExplain.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.similarities.TestAfterEffectBExplain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/explanation-formula-strings-vs-their-own-operand-values/](../../models/explanation-formula-strings-vs-their-own-operand-values/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
