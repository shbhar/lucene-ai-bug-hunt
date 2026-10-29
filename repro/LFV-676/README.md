# BengaliNormalizer's medial-visarga rule copies the next character before that character is folded, so SHA, SSA, NNA (and the other one-to-one folded letters) survive after a visarga and those spellings stop matching the SA/NA spelling the same normalizer conflates everywhere else

Bug LFV-676 · Area: analysis-common / Bengali normalizer (BengaliNormalizer.normalize; BengaliNormalizationFilter, BengaliAnalyzer and the 'bengaliNormalization' factory) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-676.html](../../bugs/LFV-676.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index or query Bengali text with the stock BengaliAnalyzer or BengaliNormalizationFilter. Its normalizer folds SHA (শ) and SSA (ষ) to SA (স) everywhere and turns a medial visarga (ঃ) into a copy of the next letter (gemination), so নিঃশব্দ ('silent'), নিঃষব্দ and নিঃসব্দ should all become নিসসব্দ (SA SA), just as the class's other gemination rule turns বিশ্ব into বিসস in an upstream test. Instead the visarga branch copies the next character before the loop has folded it and never revisits the copy, so নিঃশব্দ becomes নিশসব্দ (SHA SA) and নিঃষব্দ becomes নিষসব্দ (SSA SA): the three spellings give three different terms, and a document written with one of them is not found by a query written with another, with no error.

The test shows that BengaliNormalizationFilter's medial-visarga rule copies the next letter before it is folded, so a SHA (শ) survives after a visarga.

## Expected vs actual

Expected `নিঃশব্দ` to normalize to `নিসসব্দ` (SA SA), as the SA spelling `নিঃসব্দ` does; actual: `নিশসব্দ` (SHA SA): `term 0 expected:<নি[স]সব্দ> but was:<নি[শ]সব্দ>`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/bn/TestBengaliVisargaRule.java](src/org/apache/lucene/analysis/bn/TestBengaliVisargaRule.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.bn.TestBengaliVisargaRule
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
