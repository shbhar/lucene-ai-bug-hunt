# Hunspell Dictionary throws a raw ArrayIndexOutOfBoundsException (or NullPointerException) instead of a descriptive error when a.dic entry uses an AM morphological alias number that the.aff file does not define, although the same mistake in the AF flag-alias table gets 'Bad flag alias number'

Bug LFV-1012 · Area: analysis-common / hunspell Dictionary parsing (AM morphological aliases) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You load a hunspell dictionary whose affix file has an AM table, for example 'AM 1' and 'AM po:noun', and whose.dic has an entry 'foo&lt;TAB&gt;2' that points at an alias the table does not define (or alias 0, or 'AM 2' with only one AM line). You expect a descriptive error, as for the same mistake in the flag-alias (AF) table, which gives IllegalArgumentException 'Bad flag alias number:2' (C Hunspell, the reference implementation, warns 'bad morph. alias index' and loads the word without morphological data). Instead every Dictionary constructor fails with java.lang.ArrayIndexOutOfBoundsException 'Index 1 out of bounds for length 1' (or NullPointerException 'Cannot invoke "String.isBlank" because "morphData" is null') from Dictionary.splitMorphData, which reads morphAliases[alias - 1] with no range check, and has done so since AM aliases were added in 4.10.0 (LUCENE-5778).

The test shows that a hunspell `.dic` entry pointing at an AM morphological alias the `.aff` does not define (alias 2, table of 1) makes `Dictionary` fail with a raw `ArrayIndexOutOfBoundsException`.

## Expected vs actual

Expected: the word loads or the load fails with a descriptive `ParseException`/`IllegalArgumentException` (as an undefined AF alias gives `Bad flag alias number`); actual: `ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/hunspell/TestDictionaryUndefinedMorphAlias.java](src/org/apache/lucene/analysis/hunspell/TestDictionaryUndefinedMorphAlias.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.hunspell.TestDictionaryUndefinedMorphAlias
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
