# DirectSpellChecker with a non-default StringDistance prunes candidates by comparing the metric's score with FuzzyTermsEnum boosts, so better candidates are never inspected (regression since 7.0)

Bug LFV-240 · Area: suggest / spell checking (DirectSpellChecker.suggestSimilar with a non-default StringDistance; FuzzyTermsEnum competitive-boost pruning) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-240.html](../../bugs/LFV-240.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give DirectSpellChecker a metric such as LevenshteinDistance or JaroWinklerDistance and expect what setDistance documents: candidates drawn from the term dictionary by Damerau-Levenshtein (the internal similarity), then ranked by your metric. Since 7.0 the code stores your metric's score in ScoreTerm.boost, a field documented as the internal similarity, and then skips later candidates and tells FuzzyTermsEnum to lower its edit distance by comparing their internal similarity with that number, which is on a different scale, so better terms are never looked at. With default settings, the index {lucen, lucena, lucenb, lucenc, lucend, lucenes} and the query 'lucene', LevenshteinDistance returns 'lucen' (0.833) instead of 'lucenes' (0.857), and with maxInspections=1 and the index {lucen, lucenes} all three shipped metrics return 'lucen' although 'lucenes' scores higher under the internal similarity and under each metric.

The test shows that `DirectSpellChecker` with `LevenshteinDistance` and `setMaxInspections(1)` never inspects the better candidate, because it compares the metric's score with `FuzzyTermsEnum`'s internal-similarity boosts to prune candidates.

## Expected vs actual

Expected: `suggestSimilar('abcd', 1, SUGGEST_ALWAYS)` on the index {abc, abcde} returns abcde (closer under both similarities). Actual: abc (`expected:<abc[de]> but was:<abc[]>`).

## Files

- [src/org/apache/lucene/search/spell/TestDirectSpellCheckerLevenshteinDistance.java](src/org/apache/lucene/search/spell/TestDirectSpellCheckerLevenshteinDistance.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.spell.TestDirectSpellCheckerLevenshteinDistance
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
