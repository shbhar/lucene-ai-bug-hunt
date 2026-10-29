# StandardAnalyzer and the other JFlex-generated scanners (StandardTokenizer, UAX29URLEmailTokenizer, ClassicTokenizer, WikipediaTokenizer, HTMLStripCharFilter) silently index no tokens for a text value that ends in an unpaired high surrogate, such as a string cut inside an emoji; values longer than the scan buffer lose or split their last buffer window

Bug LFV-158 · Area: analysis / StandardTokenizer and the other JFlex-generated scanners (zzRefill in both Lucene JFlex skeletons) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-158.html](../../bugs/LFV-158.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a text field with StandardAnalyzer at default settings, and the value ends in an unpaired high surrogate, for example 'hello world' followed by U+D83D, which is what cutting a Java String inside an emoji, or decoding the JSON escape \ud83d, produces. You expect hello and world to be indexed and end to report the input length as the final offset, as happens when the same lone surrogate sits mid-text and as Lucene 7.6.0 did. Instead the scanner's refill reads the whole text, looks one char ahead for the missing low surrogate, hits end of input and reports 'no new input', so the scanner ends the stream without scanning what it just read: no tokens, final offset 0, and the document does not match title:hello; values longer than the 255-char buffer lose or split their last window instead.

The test shows that `StandardAnalyzer` at default settings drops every token of `"hello world\uD83D"` (text ending in an unpaired high surrogate, e.g. a string cut inside an emoji).

## Expected vs actual

Expected: tokens `[hello, world]` and final offset 12 (the input length). Actual: `[]` and final offset 0.

## Files

- [src/org/apache/lucene/analysis/standard/TestStandardAnalyzerTrailingHighSurrogate.java](src/org/apache/lucene/analysis/standard/TestStandardAnalyzerTrailingHighSurrogate.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.standard.TestStandardAnalyzerTrailingHighSurrogate
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
