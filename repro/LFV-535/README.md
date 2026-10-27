# CapitalizationFilter treats a second delimiter in a row ('. ', a double space) or a delimiter at the start of the token as the first character of the next word, so that word is not capitalized and misses keep, okPrefix and minWordLength ('dr. jekyll' -&gt; 'Dr. jekyll', 'new york' -&gt; 'New york', ' hello' -&gt; ' hello')

Bug LFV-535 · Area: analysis-common / miscellaneous (CapitalizationFilter, CapitalizationFilterFactory) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You put CapitalizationFilter with onlyFirstWord=false after a KeywordTokenizer and index 'dr. jekyll' or 'new york' (two spaces), and expect 'Dr. Jekyll' and 'New York', because the filter splits words at spaces and periods (a single one works: 'st.louis' -&gt; 'St.Louis') and its javadoc says it makes 'the first letter capital and the rest lower case'. Instead you get 'Dr. jekyll' and 'New york': after the delimiter that ends a word the loop skips the next character, so a second delimiter becomes the first character of the next word, the filter 'capitalizes' that delimiter and lower-cases the real first letter, and a keep word such as 'USA' after two spaces becomes 'usa'. With the default settings a token that starts with a space or a period (' hello world', '.net') keeps its first word lower case, and nothing throws.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: You get 'Dr. jekyll' and 'New york': after the delimiter that ends a word the loop skips the next character, so a second delimiter becomes the first character of the next word, the filter 'capitalizes' that delimiter and lower-cases the real first letter, and a keep word such as 'USA' after two spaces becomes 'usa'.

## Files

- [src/org/apache/lucene/analysis/miscellaneous/TestCapitalizationFilterWordBoundaries.java](src/org/apache/lucene/analysis/miscellaneous/TestCapitalizationFilterWordBoundaries.java) (JUnit test)
- [src/repro/CapitalizationWordBoundaryMain.java](src/repro/CapitalizationWordBoundaryMain.java) (main() class)

## Run it

- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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
     execute --select-class org.apache.lucene.analysis.miscellaneous.TestCapitalizationFilterWordBoundaries

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" repro.CapitalizationWordBoundaryMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
