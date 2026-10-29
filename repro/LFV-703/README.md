# SoraniAnalyzer, LatvianAnalyzer and GalicianAnalyzer load the '#' comment lines of their bundled stopwords.txt as stop words: 60 of the 122 entries of SoraniAnalyzer.getDefaultStopSet, 9 of 170 for Latvian (including a bare '#') and 1 of 161 for Galician are comment text

Bug LFV-703 · Area: analysis-common / language analyzers (default stop sets of SoraniAnalyzer, LatvianAnalyzer and GalicianAnalyzer; choice of WordlistLoader overload) · Priority: low · Impact: minor · Novelty: new

Bug page: [../../bugs/LFV-703.html](../../bugs/LFV-703.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call SoraniAnalyzer.getDefaultStopSet, LatvianAnalyzer.getDefaultStopSet or GalicianAnalyzer.getDefaultStopSet, documented as 'the default stop words set' built from the analyzer's bundled 'File containing default Kurdish/Latvian/Galician stopwords', and expect the stop words of that file; its '#' lines are comments, the format that about two dozen sibling analyzers load with the '#' comment marker and that StopFilterFactory's default 'wordset' format skips. Instead the three analyzers load the file with WordlistLoader.getWordSet(InputStream), which 'adds every line as an entry', so 60 of Sorani's 122 entries, 9 of Latvian's 170 (including a bare '#') and 1 of Galician's 161 are comment text such as '# set of kurdish stopwords', '# and' or '# galican stopwords'. The stock analyzers' tokens do not change, but code that lists, counts or reuses these sets sees the comments, and with a WhitespaceTokenizer the Latvian set removes a standalone '#' token ('lucene # search' gives [lucene, search]).

The test shows that SoraniAnalyzer, LatvianAnalyzer and GalicianAnalyzer.getDefaultStopSet() contain the '#' comment lines of their bundled stopwords.txt as stop words.

## Expected vs actual

Expected no entry starting with '#'; actual 60, 9 and 1 such entries (`expected:<Sorani [0, Latvian 0, Galician 0]> but was:<Sorani [60, Latvian 9, Galician 1]>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/ckb/TestDefaultStopSetCommentLines.java](src/org/apache/lucene/analysis/ckb/TestDefaultStopSetCommentLines.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.ckb.TestDefaultStopSetCommentLines
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
