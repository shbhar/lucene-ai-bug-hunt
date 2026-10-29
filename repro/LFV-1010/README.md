# ICUTokenizerFactory leaves a custom per-script rule file (rulefiles) open when reading or decoding it fails: parseRules closes its reader only after a successful read loop, so a rule file that is not valid UTF-8, or an I/O error while reading it, leaves the stream the ResourceLoader opened unclosed until garbage collection (every release since 4.1.0)

Bug LFV-1010 · Area: analysis / ICU (ICUTokenizerFactory per-script rule files: rulefiles, parseRules) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure ICUTokenizerFactory with rulefiles="Latn:latin-rules.rbbi" and the rule file cannot be read to the end, because it is not valid UTF-8 (for example saved as ISO-8859-1 with an accented letter in a comment) or because reading it throws an IOException; you expect inform to fail with that exception and to close the stream it opened, as it does after a successful read and as the other Lucene analysis factories do. Instead parseRules calls reader.close only after its read loop has finished normally, so the exception skips the close and the InputStream returned by the ResourceLoader stays open: a tracking loader still holds it after inform threw, and with a real file read through FilesystemResourceLoader the test framework's LeakFS reports 'file handle leaks: [InputStream(.../latin-rules.rbbi)]'. The descriptor is released only when the stream is garbage collected; a rule file that decodes but does not compile is closed before compiling and does not leak.

The test shows that `ICUTokenizerFactory.inform()` leaves a custom `rulefiles` stream open when the rule file is not valid UTF-8 (`parseRules` closes its reader only after a successful read).

## Expected vs actual

Expected: `inform()` throws `CharacterCodingException` and closes the stream it opened; actual: it throws but the stream is left open (`expected inform() to close the rule file stream it opened after failing; actual: left open`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/icu/segmentation/TestICUTokenizerFactoryRuleFileClosed.java](src/org/apache/lucene/analysis/icu/segmentation/TestICUTokenizerFactoryRuleFileClosed.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/icu/build/libs/lucene-analysis-icu-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.icu.segmentation.TestICUTokenizerFactoryRuleFileClosed
```

The module(s) analysis-icu also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/analysis-loaders-release-streams-and-temp-files-on-failure/](../../models/analysis-loaders-release-streams-and-temp-files-on-failure/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
