# Hunspell spell skips the per-entry checks on COMPOUNDRULE compound parts, so it accepts compounds that C Hunspell rejects: a KEEPCASE first part in capitalized or upper case, a FORCEUCASE last part in lower case, and a NEEDAFFIX or FORBIDDENWORD entry as a first or middle part

Bug LFV-731 · Area: analysis / hunspell (Hunspell.spell compound checking, COMPOUNDRULE) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-731.html](../../bugs/LFV-731.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You load a Hunspell dictionary that joins foo/G and bar/H by 'COMPOUNDRULE GH', give foo or bar one of the per-entry flags KEEPCASE, FORCEUCASE, NEEDAFFIX or FORBIDDENWORD, call Hunspell.spell, and expect the answer C Hunspell 1.3.2 and 1.7.2 give (and Lucene itself gives when the same parts are joined by COMPOUNDFLAG): Foobar and FOOBAR rejected when foo is KEEPCASE, foobar rejected when bar is FORCEUCASE, and foobar rejected when foo is a NEEDAFFIX virtual stem or a FORBIDDENWORD entry. Instead spell returns true in all four cases, with and without -ea, in every release since 8.9.0; on the public Swedish dictionary sv_SE it accepts lower-case rule compounds such as tolvkronorsgatan that the dictionary's FORCEUCASE flag and C Hunspell reject. The cause: checkCompoundRules takes every non-final part as a raw dictionary.lookupWord result, without the NEEDAFFIX, FORBIDDENWORD, COMPOUNDFORBIDFLAG and first-part case (KEEPCASE) filters, and checkLastCompoundPart never applies the FORCEUCASE check (hasForceUCaseProblem) that the COMPOUNDFLAG path uses.

The test shows that `Hunspell.spell` ignores FORCEUCASE on the last part of a COMPOUNDRULE compound: dictionary `COMPOUNDMIN 1`, `FORCEUCASE U`, `COMPOUNDRULE GH`, entries `foo/G` `bar/HU`.

## Expected vs actual

Expected (hunspell(5) FORCEUCASE forces capitalization of the whole compound; C Hunspell rejects it): `spell("foobar")` is false. Actual: true (`spell("foobar") expected false, was true`).

## Files

- [src/org/apache/lucene/analysis/hunspell/TestCompoundRuleForceUCase.java](src/org/apache/lucene/analysis/hunspell/TestCompoundRuleForceUCase.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.hunspell.TestCompoundRuleForceUCase
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/hunspell-synthetic-dictionary-differential/](../../models/hunspell-synthetic-dictionary-differential/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
