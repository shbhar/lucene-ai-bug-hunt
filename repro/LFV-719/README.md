# ICUTokenizerFactory keeps the UTF-8 byte order mark at the start of a custom per-script rule file (rulefiles) as rule text, so the file either fails with a misleading ICU syntax error or loads without error and the tokenizer then emits no tokens for that script (every release since 4.1.0)

Bug LFV-719 · Area: analysis / ICU (ICUTokenizerFactory per-script rule files: rulefiles, parseRules) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure ICUTokenizerFactory with rulefiles="Latn:latin.rbbi" and a rule file saved as UTF-8 with a byte order mark (BOM), as editors such as Windows Notepad often write and as ICU's own rule-file compiler genbrk accepts (it strips the BOM, and its man page recommends one), and expect the same tokens as with the same file without the BOM. Instead ICUTokenizerFactory.parseRules decodes the BOM to U+FEFF and passes it to ICU's RuleBasedBreakIterator(String) as rule text: a file that starts with the rule '.+ {200};' (alone or after '#' comment lines) compiles with U+FEFF as a literal prefix of that rule, so 'ab cd' gives no tokens at all instead of ['ab cd'], and a file that starts with a comment and '!!forward;' (the shape of Lucene's Latin-break-only-on-whitespace.rbbi) fails with 'IllegalArgumentException: Error 66052 at line 2 column 1'. Nothing on disk is corrupted, but in the silent case all text of the tailored script disappears from the analyzed output with no error.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: ICUTokenizerFactory.parseRules decodes the BOM to U+FEFF and passes it to ICU's RuleBasedBreakIterator(String) as rule text: a file that starts with the rule '.+ {200};' (alone or after '#' comment lines) compiles with U+FEFF as a literal prefix of that rule, so 'ab cd' gives no tokens at all instead of ['ab cd'], and a file that starts with a comment and '!!forward;' (the shape of Lucene's Latin-break-only-on-whitespace.rbbi) fails with 'IllegalArgumentException: Error 66052 at line 2 column 1'.

## Files

- [src/org/apache/lucene/analysis/icu/segmentation/TestICUTokenizerFactoryRuleFileBom.java](src/org/apache/lucene/analysis/icu/segmentation/TestICUTokenizerFactoryRuleFileBom.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/icu/build/libs/lucene-analysis-icu-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.icu.segmentation.TestICUTokenizerFactoryRuleFileBom
```

The module(s) analysis-icu also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
