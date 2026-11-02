# WordBreakSpellChecker.suggestWordCombinations looks every combined word up in the field of terms[0], so a leading SEPARATOR_TERM silently disables all combination suggestions

Finding LFV-1073 · Area: suggest / spell (WordBreakSpellChecker word combinations) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1073.html](../../bugs/LFV-1073.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call WordBreakSpellChecker.suggestWordCombinations with the class's own SEPARATOR_TERM in front of your terms, for example [SEPARATOR_TERM, f:sun, f:flower] on an index that holds f:sunflower, and expect the same 'sunflower' suggestion you get without the separator, because the separator is documented only to stop two adjacent terms from being combined. Instead you get no suggestion and no error: the code looks every combined word up in the field of terms[0], which for a leading separator is the empty field "", where nothing is indexed. The same line also picks the field when one call passes terms of several fields.

## Expected vs actual

- Expected: One suggestion, `sunflower`, as for the same call without the separator.
- Actual: 0 suggestions and no error: `AssertionError: expected:<1> but was:<0>`, with assertions on and off.

## Files

- [src/org/apache/lucene/search/spell/TestWordBreakCombineLeadingSeparator.java](src/org/apache/lucene/search/spell/TestWordBreakCombineLeadingSeparator.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
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
     execute --select-class org.apache.lucene.search.spell.TestWordBreakCombineLeadingSeparator
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestWordBreakCombineLeadingSeparator.java](TestWordBreakCombineLeadingSeparator.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
