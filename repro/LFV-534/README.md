# SerbianAnalyzer.normalize only lower-cases and skips SerbianNormalizationFilter (Cyrillic to Latin, diacritics removed), so prefix, wildcard, fuzzy, regexp and range queries typed in Cyrillic or with č ć š ž đ miss the indexed terms

Bug LFV-534 · Area: analysis-common / language analyzers (SerbianAnalyzer.normalize, used by the query parsers for multi-term queries) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-534.html](../../bugs/LFV-534.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index Serbian text with SerbianAnalyzer, which lower-cases, stems and then maps Cyrillic to Latin and removes diacritics (SerbianNormalizationFilter), so 'Београд' is stored as 'beograd' and 'Čačak' as 'cack', and you search with a prefix, wildcard, fuzzy, regexp or range query through a query parser, which passes the term through Analyzer.normalize, documented to give 'the representation that it would have in the index'. SerbianAnalyzer.normalize only lower-cases, so 'Беогр*' becomes the prefix 'беогр' and 'Čač*' becomes 'čač', and both find nothing while 'Beogr*' and 'Cac*' find the documents. Directly, normalize("Ђак") returns 'ђак' while the index holds 'djak', even with stemming switched off.

`SerbianAnalyzer.normalize` only lower-cases and skips `SerbianNormalizationFilter`, so it does not return the form the analyzer indexes (which the query parsers rely on for prefix, wildcard, fuzzy, regexp and range terms).

## Expected vs actual

Expected: `normalize("f", "Ђак")` = `djak` (the indexed term); actual: `ђак`.

## Files

- [src/org/apache/lucene/analysis/sr/TestSerbianAnalyzerNormalize.java](src/org/apache/lucene/analysis/sr/TestSerbianAnalyzerNormalize.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.sr.TestSerbianAnalyzerNormalize
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
