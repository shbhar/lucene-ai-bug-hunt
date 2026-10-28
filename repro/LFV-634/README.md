# Hunspell getAllWordForms, generateAllSimpleWords and compress never generate a suffix that only a prefix's continuation class allows: with ampere/U and 'PFX U 0 kilo/S', spell accepts amperes and kiloamperes, but getAllWordForms("ampere") returns only [ampere, kiloampere]

Bug LFV-634 · Area: analysis / hunspell (WordFormGenerator: getAllWordForms, generateAllSimpleWords, compress) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-634.html](../../bugs/LFV-634.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You load a Hunspell dictionary in which a prefix rule's continuation class allows a suffix, for example 'PFX U 0 kilo/S' and 'SFX S 0 s' with the entry ampere/U, and ask Hunspell.getAllWordForms("ampere") for all forms of that entry. You expect ampere, amperes, kiloampere and kiloamperes: Lucene's own spell accepts all four and analyzeSimpleWord analyses each one as a form of ampere/U, as C Hunspell 1.7.2 also does. Instead you get only [ampere, kiloampere], because WordFormGenerator applies suffixes only from the entry's own flags and then forbids any suffix once a prefix has been applied (WordFormGenerator.java:378); generateAllSimpleWords misses the same words, and compress suggests an unneeded flag (for work/P with 'PFX P 0 re/S', compress([work, rework, reworks]) proposes work/PS, which also adds the unrequested word works).

## Expected vs actual

- Expected: `[ampere, amperes, kiloampere, kiloamperes]`; `generateAllSimpleWords` = `work, rework, reworks`; `compress` = `toEdit=[work/P], extra=`.
- Actual: the words `[ampere, kiloampere]` (raw list `[ampere, ampere, kiloampere]`); `work, rework`; `toEdit=[work/PS], extra=[works]`: 3 of 4 tests fail with assertions on and off, the controls pass.

## Files

- [src/org/apache/lucene/analysis/hunspell/TestWordFormsPrefixEnabledSuffix.java](src/org/apache/lucene/analysis/hunspell/TestWordFormsPrefixEnabledSuffix.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.hunspell.TestWordFormsPrefixEnabledSuffix
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestWordFormsPrefixEnabledSuffix.java](TestWordFormsPrefixEnabledSuffix.java)
- [2-TestWordFormsPrefixEnabledSuffix.java](2-TestWordFormsPrefixEnabledSuffix.java)
- [TestPrefixEnabledSuffixWordForms.java](TestPrefixEnabledSuffixWordForms.java)

Formal models and scripts this bug's part of the hunt used: [../../models/hunspell-wordform-generator-munch-differential/](../../models/hunspell-wordform-generator-munch-differential/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
