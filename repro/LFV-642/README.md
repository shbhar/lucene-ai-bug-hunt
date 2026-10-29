# Hunspell COMPLEXPREFIXES accepts a second prefix after an inner prefix rule whose continuation class does not allow it: with 'PFX A 0 re', 'PFX C 0 un', 'PFX C 0 dis/A' and foo/C, spell("reunfoo") is true and HunspellStemFilter stems reunfoo to foo, although only dis, not un, allows re (a regression since 9.5.0)

Bug LFV-642 · Area: analysis / hunspell (Stemmer affix compatibility: COMPLEXPREFIXES twofold prefixes) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-642.html](../../bugs/LFV-642.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You load a hunspell dictionary that sets COMPLEXPREFIXES (twofold prefix stripping) with the outer prefix 'PFX A 0 re', an inner prefix class C with two rules, 'un' without a continuation class and 'dis/A', and the root foo/C, and expect what hunspell(5), C Hunspell 1.3.2 and 1.7.2 and Lucene up to 9.4.2 do: re may only precede dis, so redisfoo is a word and reunfoo is not, just as Lucene still checks the mirror-image twofold suffixes rule by rule. Instead spell("reunfoo") is true, getRoots("reunfoo") is [foo] and HunspellStemFilter turns the token reunfoo into foo, because since 9.5.0 Stemmer.isAffixCompatible returns true for any prefix stripped after another prefix (Stemmer.java:541, 566) and the only remaining gate is the dictionary-wide check that some prefix rule carries the flag A (Dictionary.isSecondStagePrefix).

The test shows that with COMPLEXPREFIXES, `PFX A 0 re`, `PFX C 0 un`, `PFX C 0 dis/A` and `foo/C`, Hunspell accepts `re` before `un` although only `dis` carries A.

## Expected vs actual

Expected: `spell("reunfoo")` is false (as in C Hunspell and Lucene 9.4.2); actual (-ea and -da): true, because `Stemmer.isAffixCompatible` returns true for any prefix after a prefix (Stemmer.java:566).

## Files

- [src/org/apache/lucene/analysis/hunspell/TestComplexPrefixesSecondPrefix.java](src/org/apache/lucene/analysis/hunspell/TestComplexPrefixesSecondPrefix.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.hunspell.TestComplexPrefixesSecondPrefix
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
