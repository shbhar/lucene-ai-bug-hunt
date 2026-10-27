# LuceneLevenshteinDistance.getDistance returns 0 for two empty strings and max(n, m) (for example 3.0) when one string is empty, instead of a similarity score (a 2011 regression from LUCENE-3662)

Bug LFV-350 · Area: suggest / spell checking (LuceneLevenshteinDistance.getDistance, the StringDistance class behind DirectSpellChecker.INTERNAL_LEVENSHTEIN) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

LuceneLevenshteinDistance is documented as a similarity (StringDistance: 1 means identical, 0 maximally different) that is 'consistent' with FuzzyTermsEnum, and for two non-empty strings it returns 1 - distance / min(length). When a string is empty it instead returns a raw edit distance: 0 for getDistance("", "") and max(n, m) for one empty side, so getDistance("", "abc") = 3.0, three times the score of identical strings. Through DirectSpellChecker set up with new LuceneLevenshteinDistance, minPrefix 0 and minQueryLength 1, an indexed empty term is suggested for the query "a" with score 1.0, while the default internal scoring filters it out.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/search/spell/TestLuceneLevenshteinDistanceEmpty.java](src/org/apache/lucene/search/spell/TestLuceneLevenshteinDistanceEmpty.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.search.spell.TestLuceneLevenshteinDistanceEmpty
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/fuzzy-levenshtein/](../../models/fuzzy-levenshtein/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
