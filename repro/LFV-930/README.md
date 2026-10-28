# SimplePatternSplitTokenizerFactory and SimplePatternTokenizerFactory javadoc whitespace examples (pattern="[ \t\r\n]+" and "[^ \t\r\n]+" in an XML attribute, so the backslashes stay literal) never split on whitespace: since 9.0.0 RegExp rejects them with IllegalArgumentException (the rejection is intended, LUCENE-9370), on 6.5 to 8.x they split on the letters t, r, n; the RegExp error also prints the rejected letter as a decimal code point ('invalid character class \116' for \t)

Bug LFV-930 · Area: analysis-common / SimplePattern(Split)TokenizerFactory javadoc examples; core / util.automaton RegExp error message · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You copy the class javadoc example of SimplePatternSplitTokenizerFactory, pattern="[ \t\r\n]+" (or SimplePatternTokenizerFactory's "[^ \t\r\n]+"), which the javadoc offers 'to match tokens delimited by simple whitespace characters', and expect tokens split on spaces, tabs and newlines. In XML the backslashes stay literal, and since 9.0.0 RegExp deliberately rejects a backslash before a letter other than s, S, w, W, d, D (LUCENE-9370, MIGRATE.md), so building the factory throws IllegalArgumentException 'invalid character class \116'; on 6.5 to 8.x the same pattern was accepted but split on the letters t, r and n instead. The message is a second, cosmetic defect: RegExp.java:1430 concatenates the int code point returned by next, so \t is reported as '\116' and \q as '\113'.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/analysis/pattern/TestSimplePatternJavadocExamples.java](src/org/apache/lucene/analysis/pattern/TestSimplePatternJavadocExamples.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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
     execute --select-class org.apache.lucene.analysis.pattern.TestSimplePatternJavadocExamples
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
