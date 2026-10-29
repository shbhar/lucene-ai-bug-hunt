# Hunspell getAllWordForms and generateAllSimpleWords return no forms for an entry with a COMPOUNDBEGIN, COMPOUNDMIDDLE or COMPOUNDEND flag and drop every affix whose continuation class has one, although spell accepts those words on their own: with 'COMPOUNDBEGIN B' and foo/AB, getAllWordForms("foo") = instead of [foo, foos] (Swedish sv_SE: förökelse/AEIXY gives )

Bug LFV-639 · Area: analysis / hunspell (WordFormGenerator: getAllWordForms, generateAllSimpleWords) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-639.html](../../bugs/LFV-639.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You load a Hunspell dictionary in which an ordinary entry carries a compound-position flag, for example 'COMPOUNDBEGIN B', 'SFX A 0 s.' and the entry foo/AB (foo may also start a compound such as foobar), and ask Hunspell.getAllWordForms("foo") for all forms of that entry. You expect [foo, foos]: Lucene's own spell accepts both and analyzeSimpleWord attributes them to foo/AB, as C Hunspell 1.7.2 also does, because COMPOUNDBEGIN/MIDDLE/END only allow a word to take part in compounds while ONLYINCOMPOUND is the flag that forbids it on its own. Instead you get , because WordFormGenerator.shouldConsiderAtAll (WordFormGenerator.java:261-273) rejects the three compound-position flags together with FORBIDDENWORD and ONLYINCOMPOUND, both for the entry (so the entry yields nothing) and for affix continuation classes (so with 'SFX C 0 n/W' and W = COMPOUNDEND, getAllWordForms("Computer") never yields Computern, a word in Lucene's own germancompounding.good list); generateAllSimpleWords skips the same entries.

The test shows that `Hunspell.getAllWordForms("foo")` returns nothing for the entry `foo/AB` when B is the COMPOUNDBEGIN flag, although spell() accepts foo and foos.

## Expected vs actual

Expected, per the getAllWordForms javadoc ("all word forms"): `[foo, foos]`. Actual: `[]`.

## Files

- [src/org/apache/lucene/analysis/hunspell/TestWordFormsCompoundBeginEntry.java](src/org/apache/lucene/analysis/hunspell/TestWordFormsCompoundBeginEntry.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.hunspell.TestWordFormsCompoundBeginEntry
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
