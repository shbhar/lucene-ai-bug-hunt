# Hunspell: the part of an affix condition that overlaps the strip is checked with java.util.regex without escaping the dash, so a bracket such as [a-c] there means the range a..c instead of the set {a, -, c} that hunspell(5) defines, and the rule is applied or dropped wrongly

Bug LFV-376 · Area: analysis / hunspell (AffixCondition: affix conditions used by Hunspell.spell/suggest and by stemming through Stemmer/HunspellStemFilter) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-376.html](../../bugs/LFV-376.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You load a Hunspell dictionary with the rules 'SFX A b x [a-c]' and 'SFX A b y [^a-c]' on the root ab and expect [a-c] to mean what hunspell(5) says, the set {a, -, c} ('Dash hasn't got special meaning'), so the first rule never applies (ab ends in b) and the second one does: C Hunspell 1.3.2 and 1.7.2 reject ax and accept ay. Instead Lucene's Hunspell.spell accepts ax and rejects ay (likewise xa and ya for the PFX twins on ba), and HunspellStemFilter stems ax to ab and leaves ay unstemmed. AffixCondition.compile decides the part of a condition that overlaps the strip once, at load time, with String.matches (java.util.regex) on the raw condition text, which reads [a-c] as a range, while the part checked against the stem goes through escapeDash; a reversed pseudo-range such as [c-a] even makes java.util.regex throw, and the rule is silently turned into 'never applies'.

The test shows that when an affix condition overlaps the strip, Hunspell reads the dash in `[^a-c]` as a regex range, but hunspell(5) says the dash has no special meaning (rule `SFX A b y [^a-c]`, root `ab`).

## Expected vs actual

Expected: `spell("ay")` is true (`b` is not one of `a`, `-`, `c`; C Hunspell accepts `ay`). Actual: false.

## Files

- [src/org/apache/lucene/analysis/hunspell/TestAffixConditionDashInStrip.java](src/org/apache/lucene/analysis/hunspell/TestAffixConditionDashInStrip.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.hunspell.TestAffixConditionDashInStrip
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
