# FileDictionary treats its fieldDelimiter as a regular expression, so a delimiter such as '|', '.', '+' or '$' makes a valid suggestion file fail to load or silently mis-parse

Bug LFV-478 · Area: suggest / FileDictionary (text-file dictionary for Lookup.build) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a suggester from a text file with lines such as 'foo bar|10|p1', read with new FileDictionary(reader, "|"), and expect the entry (suggestion 'foo bar', weight 10, payload 'p1') that the class javadoc's line format describes. Instead FileDictionary passes the delimiter to String.split, which reads it as a regular expression: '|' (an empty alternation) splits between every character, so getEntryIterator throws IllegalArgumentException 'More than 3 fields in one line'; '.' and '+' throw ArrayIndexOutOfBoundsException and PatternSyntaxException. With '$' or '^' no line is ever split, so every line silently becomes one suggestion such as 'foo bar$10$p1' with weight 1 and no payload, and a suggester built from it returns that key.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: FileDictionary passes the delimiter to String.split, which reads it as a regular expression: '|' (an empty alternation) splits between every character, so getEntryIterator throws IllegalArgumentException 'More than 3 fields in one line'; '.' and '+' throw ArrayIndexOutOfBoundsException and PatternSyntaxException.

## Files

- [src/org/apache/lucene/search/suggest/TestFileDictionaryDelimiterIsRegex.java](src/org/apache/lucene/search/suggest/TestFileDictionaryDelimiterIsRegex.java) (JUnit test)

## Run it

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
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.TestFileDictionaryDelimiterIsRegex
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
