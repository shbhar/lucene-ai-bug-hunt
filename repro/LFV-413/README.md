# SweetSpotSimilarity with a length plateau (setLengthNormFactors with min &gt;= 2) breaks Similarity's norm-monotonicity contract, so default top-k search silently misses the best hits

Bug LFV-413 · Area: misc / similarities (SweetSpotSimilarity) with core impacts-based TOP_SCORES pruning · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-413.html](../../bugs/LFV-413.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You score a field with SweetSpotSimilarity and setLengthNormFactors(10, 20, 0.5f), index mostly 10-term fields plus some shorter ones, and run searcher.search(new TermQuery(new Term("f", "t")), 10) on a term with more than 1,000 matches, expecting the same top hits as an exhaustive search. Instead a 10-term document that contains the term twice (score 1.414) is missing and a document that contains it once (score 1.0) comes first. Below the plateau a 2-term field scores lower than a 10-term field although its norm is smaller, which breaks the rule that a greater norm never scores higher; the postings writer relies on that rule, keeps only the short field's (freq, norm) pair for the block, advertises a block maximum of 0.471, and top-k search skips the block.

The test shows that with SweetSpotSimilarity.setLengthNormFactors(10, 20, 0.5f), the default IndexSearcher.search(q, 1) misses the best hit, because a shorter-than-plateau field makes the postings block's impacts underestimate its max score and TOP_SCORES skips the block.

## Expected vs actual

Expected: search(q, 1) returns the exhaustive top hit, doc 1025 (score 1.41). Actual: doc 0 (score 1.0) (`expected:<1025> but was:<0>`), with -ea and -da.

## Files

- [src/org/apache/lucene/misc/TestSweetSpotTopScores.java](src/org/apache/lucene/misc/TestSweetSpotTopScores.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
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
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
