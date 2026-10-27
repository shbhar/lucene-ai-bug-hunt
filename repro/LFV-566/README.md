# SpellChecker.suggestSimilar with SuggestWordFrequencyComparator drops more frequent suggestions: once the queue is full it raises the accuracy cutoff to the score of the least frequent queued word, which is a valid bound only for a score-first order (since 3.1, LUCENE-2479)

Bug LFV-566 · Area: suggest / spell: SpellChecker.suggestSimilar (index-based n-gram spell checker; comparator vs. full-queue accuracy cutoff) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an index-based SpellChecker with the shipped SuggestWordFrequencyComparator ('Frequency first, then score') and ask for suggestions restricted to a field, expecting the most frequent qualifying words among the retrieved candidates. Once the queue holds numSug words, suggestSimilar raises its accuracy cutoff to the score of the queue's head, which under this comparator is the least frequent queued word, so a later candidate that is more frequent but scores slightly lower is rejected before its frequency is even read. With 'receive' in 50 documents and one-edit neighbours such as 'relieve', 'reprieve' and 'retrieve' in one document each, suggestSimilar('recieve', 1, reader, field, SUGGEST_WHEN_NOT_IN_INDEX) returns [relieve] instead of receive (numSug 1-3 all leave it out; numSug 4 ranks it first).

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/search/spell/TestSpellCheckerFrequencyComparatorCutoff.java](src/org/apache/lucene/search/spell/TestSpellCheckerFrequencyComparatorCutoff.java) (JUnit test)
- [src/SpellCheckerFrequencyComparatorCutoffMain.java](src/SpellCheckerFrequencyComparatorCutoffMain.java) (main() class)

## Run it

- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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
     execute --select-class org.apache.lucene.search.spell.TestSpellCheckerFrequencyComparatorCutoff

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" SpellCheckerFrequencyComparatorCutoffMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
