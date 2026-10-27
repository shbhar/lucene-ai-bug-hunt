# ConcatenateGraphFilter treats a custom tokenSeparator as its internal one-byte separator label: an ASCII separator is doubled wherever it already occurs inside a token ('e-mail' becomes 'e--mail'), and a non-ASCII separator fails an assertion with -ea or is written as one truncated byte with -da (U+2013 becomes U+0013, U+00B7 a lone 0xB7 byte, i.e. invalid UTF-8)

Bug LFV-517 · Area: analysis-common / ConcatenateGraphFilter and ConcatenateGraphFilterFactory (custom tokenSeparator) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You join tokens with ConcatenateGraphFilter (or the concatenateGraph factory) and a custom tokenSeparator, and expect what the javadoc says, every token joined with that separator: the single token 'e-mail' stays 'e-mail', 'e-mail' and 'x' joined with '-' give 'e-mail-x', and 'new' and 'york' joined with U+2013 give the UTF-8 bytes of new, U+2013, york. Instead the filter passes the custom separator to an escaper written for its internal one-byte label U+001F, so every occurrence of an ASCII separator inside a token is doubled ('e--mail', 'e--mail-x', and 'new york' for a keyword token with a space separator). A non-ASCII separator fails the escaper's assertion with -ea and, with -da, is cut to its low byte: U+2013 gives 'new' U+0013 'york', and U+00B7 gives the invalid UTF-8 term [6e 65 77 b7 79 6f 72 6b].

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The filter passes the custom separator to an escaper written for its internal one-byte label U+001F, so every occurrence of an ASCII separator inside a token is doubled ('e--mail', 'e--mail-x', and 'new york' for a keyword token with a space separator).

## Files

- [src/org/apache/lucene/analysis/miscellaneous/TestConcatenateGraphCustomSeparator.java](src/org/apache/lucene/analysis/miscellaneous/TestConcatenateGraphCustomSeparator.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.miscellaneous.TestConcatenateGraphCustomSeparator
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
