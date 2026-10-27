# SweetSpotSimilarity with a length plateau (setLengthNormFactors with min &gt;= 2) breaks Similarity's norm-monotonicity contract, so default top-k search silently misses the best hits

Bug LFV-413 · Area: misc / similarities (SweetSpotSimilarity) with core impacts-based TOP_SCORES pruning · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-413.html](../../bugs/LFV-413.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You score a field with SweetSpotSimilarity and setLengthNormFactors(10, 20, 0.5f), index mostly 10-term fields plus some shorter ones, and run searcher.search(new TermQuery(new Term("f", "t")), 10) on a term with more than 1,000 matches, expecting the same top hits as an exhaustive search. Instead a 10-term document that contains the term twice (score 1.414) is missing and a document that contains it once (score 1.0) comes first. Below the plateau a 2-term field scores lower than a 10-term field although its norm is smaller, which breaks the rule that a greater norm never scores higher; the postings writer relies on that rule, keeps only the short field's (freq, norm) pair for the block, advertises a block maximum of 0.471, and top-k search skips the block.

## Expected vs actual

- Expected: `search(q, 10)` returns doc 2150 (1.4142135) first, like the search that counts every hit.
- Actual: It returns docs 0-9 (1.0 each) and misses doc 2150; `getMaxScore(2150)` is 0.47140452; 5 of 6 tests fail.

## Files

- [src/org/apache/lucene/misc/TestSweetSpotTopScores.java](src/org/apache/lucene/misc/TestSweetSpotTopScores.java) (JUnit test)
- [src/SweetSpotTopScoresMain.java](src/SweetSpotTopScoresMain.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.TestSweetSpotTopScores

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" SweetSpotTopScoresMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestSweetSpotSimilarityTopScores.java](TestSweetSpotSimilarityTopScores.java)
- [TestSweetSpotPlateauPruningV1.java](TestSweetSpotPlateauPruningV1.java)
- [SweetSpotPlainSearcherCheck.java](SweetSpotPlainSearcherCheck.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
