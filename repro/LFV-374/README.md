# Hunspell: a bracket or dot affix condition with fewer character patterns than the strip is checked against the wrong end of the strip (a suffix rule at the strip's start, a prefix rule at its end), so the rule never applies when it should, or always applies when it should not

Bug LFV-374 · Area: analysis / hunspell (affix conditions: Dictionary loading, Hunspell.spell/getRoots, Stemmer and HunspellStemFilter) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You load a Hunspell dictionary with the suffix rule 'SFX A abc d [c]' (strip abc, add d, condition [c]) on the root xabc and expect the condition to be tested where Hunspell lines it up, at the end of the root, which for a condition shorter than the strip is the end of the strip: xd should be a correct word with stem xabc and 'SFX A abc e [a]' should never apply, as C Hunspell 1.3.2 answers and as Lucene itself answers when the same condition is written as the literal c. Instead Lucene rejects xd and accepts xe as a form of xabc, and the prefix rules 'PFX B abc d [a]' and 'PFX B abc e [c]' on the root abcx are reversed the same way (dx rejected, ex accepted); spell, getRoots and HunspellStemFilter give these wrong verdicts and stems with no error. The cause is AffixCondition.compile: for a condition with or. and no more character patterns than the strip, it decides the rule once at load by matching the strip against 'kind == PREFIX ? ".*" + condition: condition + ".*"', which tests the strip's start for SFX and its end for PFX, the two templates swapped between the kinds.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Lucene rejects xd and accepts xe as a form of xabc, and the prefix rules 'PFX B abc d [a]' and 'PFX B abc e [c]' on the root abcx are reversed the same way (dx rejected, ex accepted); spell, getRoots and HunspellStemFilter give these wrong verdicts and stems with no error.

## Files

- [src/org/apache/lucene/analysis/hunspell/TestAffixConditionShorterThanStrip.java](src/org/apache/lucene/analysis/hunspell/TestAffixConditionShorterThanStrip.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.hunspell.TestAffixConditionShorterThanStrip
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/hunspell/](../../models/hunspell/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
