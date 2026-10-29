# JapaneseCompletionFilterFactory upper-cases the default mode name instead of the configured value, so mode="query" is rejected with IllegalArgumentException while the sibling JapaneseTokenizerFactory accepts lower-case modes (minor consistency defect; no javadoc promises case-insensitive values)

Bug LFV-335 · Area: analysis / kuromoji (JapaneseCompletionFilterFactory 'mode' argument parsing, SPI name japaneseCompletion) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure the Japanese completion filter through its factory with mode="query", for example CustomAnalyzer.builder.withTokenizer("japanese", "mode", "normal").addTokenFilter("japaneseCompletion", "mode", "query"), the same way the sibling Japanese tokenizer factory accepts mode="search". You expect a QUERY-mode filter, because the factory's own parsing code calls toUpperCase(Locale.ROOT) on the mode. Instead the constructor throws IllegalArgumentException 'No enum constant...JapaneseCompletionFilter.Mode.query': the toUpperCase is applied to the default name "INDEX", which is already upper case, so the configured value reaches the case-sensitive Enum.valueOf unchanged and only the exact names INDEX and QUERY work.

The test shows that `JapaneseCompletionFilterFactory` rejects `mode=query`, because its `toUpperCase(Locale.ROOT)` runs on the default name `INDEX` instead of on the configured value.

## Expected vs actual

Expected: `mode=query` is upper-cased to `QUERY`. Actual: `IllegalArgumentException: No enum constant org.apache.lucene.analysis.ja.JapaneseCompletionFilter.Mode.query` (with `-ea` and `-da`).

## Files

- [src/org/apache/lucene/analysis/ja/TestJapaneseCompletionFilterFactoryLowerCaseMode.java](src/org/apache/lucene/analysis/ja/TestJapaneseCompletionFilterFactoryLowerCaseMode.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/kuromoji/build/libs/lucene-analysis-kuromoji-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ja.TestJapaneseCompletionFilterFactoryLowerCaseMode
```

The module(s) analysis-kuromoji also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
