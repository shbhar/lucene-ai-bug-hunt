# HTMLStripCharFilter leaves hexadecimal character references with six hex digits (&amp;#x10FFFD; &amp;#x100000; the zero-padded &amp;#x0000E9) undecoded, because its length check counts the leading 'x' and so lets only five hex digits through

Bug LFV-289 · Area: analysis / HTMLStripCharFilter (JFlex grammar, numeric character references; also the 'htmlStrip' char filter SPI) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run HTML through HTMLStripCharFilter and expect every numeric character reference up to U+10FFFF to be decoded, as the scanner's own comment says ('10FFFF: max 6 hex chars') and as its decimal branch does for &amp;#1114111; and the zero-padded &amp;#0000233;. Instead a hex reference with six hex digits, such as &amp;#x10FFFD; &amp;#x100000; or &amp;#x0000E9; stays in the output as literal text: the length that is checked includes the leading 'x', so only five hex digits pass. Through StandardTokenizer, 'b&amp;#x000061;d' is indexed as the tokens b, x000061 and d, and a TermQuery for 'bad' does not find the document.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: A hex reference with six hex digits, such as &amp;#x10FFFD; &amp;#x100000; or &amp;#x0000E9; stays in the output as literal text: the length that is checked includes the leading 'x', so only five hex digits pass.

## Files

- [src/org/apache/lucene/analysis/charfilter/TestHTMLStripHexCharRefDigits.java](src/org/apache/lucene/analysis/charfilter/TestHTMLStripHexCharRefDigits.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
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
     execute --select-class org.apache.lucene.analysis.charfilter.TestHTMLStripHexCharRefDigits
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
