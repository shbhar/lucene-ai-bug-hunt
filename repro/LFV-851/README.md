# WordDelimiterGraphFilterFactory's types option (and the deprecated WordDelimiterFilterFactory's) refills the whole Latin-1 type table from getType instead of the filter's default table, so any rule, even one that restates a character's own type, silently changes how 8 other characters (¹ ² ³ ¼ ½ ¾ ª º) are tokenized

Bug LFV-851 · Area: analysis / WordDelimiterGraphFilterFactory and WordDelimiterFilterFactory types option (parseTypes) versus the default WordDelimiterIterator character-type table · Priority: low · Impact: minor · Novelty: new

Bug page: [../../bugs/LFV-851.html](../../bugs/LFV-851.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give WordDelimiterGraphFilterFactory a types file with one rule for a character that is not in your text (for example '$ =&gt; DIGIT', or even '- =&gt; SUBWORD_DELIM', which '-' already is) and expect every other character to keep the type it has without the option. Instead parseTypes fills all 256 Latin-1 entries from WordDelimiterIterator.getType (the Unicode general category), while the table the filter uses without the option is built from Character.isLowerCase/isUpperCase/isDigit; the two disagree on exactly 8 characters, so 'm² x³ 1½ ¼' analyzes to [m, ², x, ³, 1½, ¼] instead of [m, x, 1] and 'ªB' is no longer split. Nothing reports an error; a small change that seeds the Latin-1 entries from the default table fixes both factories.

The test shows that a WordDelimiterGraphFilterFactory types file with the no-op rule '- =&gt; SUBWORD_DELIM' changes how the unlisted character ² is tokenized.

## Expected vs actual

Expected: 'm²' gives [m], as without the types file. Actual (-ea and -da): [m, ²].

## Files

- [src/org/apache/lucene/analysis/miscellaneous/TestWdgfTypesRuleKeepsOtherTypes.java](src/org/apache/lucene/analysis/miscellaneous/TestWdgfTypesRuleKeepsOtherTypes.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.miscellaneous.TestWdgfTypesRuleKeepsOtherTypes
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
