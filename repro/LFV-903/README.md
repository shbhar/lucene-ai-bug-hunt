# MultiSimilarity does not document that only sims[0] computes the field norm, so the other sub-similarities' index-time norm settings (discountOverlaps, a computeNorm override) are silently ignored and the array order decides the stored norm, and its inherited getDiscountOverlaps always returns true (documentation defect; on one index the score is an exact sum in either order)

Bug LFV-903 · Area: core / search similarities (MultiSimilarity: undocumented norm ownership, inherited getDiscountOverlaps) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You combine two sub-similarities in a MultiSimilarity, for example BM25 with discountOverlaps=true and BM25 with discountOverlaps=false, and use it for indexing and searching; the javadoc says only that it 'will sum the scores of the provided sims' (CombSUM), so you expect both sub-similarities' settings to count. In fact computeNorm returns sims[0].computeNorm(state), so only the first sub-similarity writes the field's single norm and every sub-scorer decodes that norm: the second one's discountOverlaps (or its own computeNorm encoding) is silently ignored, swapping the array order changes the stored norms and therefore the scores (0.7534208 for [discount, noDiscount] vs 0.6862843 for [noDiscount, discount] in an independent repro), and the inherited getDiscountOverlaps returns true even when sims[0] counts overlap tokens. The single norm is the intended one-norm-per-field design and on any one index the score is an exact sum in either order; what is missing is the documentation (and a correct getter), so the fix is a javadoc sentence, optionally plus passing sims[0].getDiscountOverlaps to the super constructor.

## Expected vs actual

- Expected: You expect both sub-similarities' settings to count.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/search/similarities/TestMultiSimilarityNorms.java](src/org/apache/lucene/search/similarities/TestMultiSimilarityNorms.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.similarities.TestMultiSimilarityNorms
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
