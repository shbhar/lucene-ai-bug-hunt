# ICUCollationKeyAnalyzer does not override attributeFactory(String), so Analyzer.normalize returns the plain UTF-8 bytes instead of the ICU collation key, and classic QueryParser range queries on an ICU collation-key field match the wrong documents

Finding LFV-1053 · Area: analysis-icu / collation (ICUCollationKeyAnalyzer vs Analyzer#normalize; classic QueryParser range bounds) · Priority: medium · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1053.html](../../bugs/LFV-1053.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index the values a, b, c, d and e in a TextField analyzed with new ICUCollationKeyAnalyzer(Collator.getInstance(ULocale.ROOT)) and parse "[b TO d]" with the classic QueryParser on the same analyzer; you expect the 3 documents b, c and d, which is what the java.text twin CollationKeyAnalyzer gives. You get 0: the parser builds both range bounds with Analyzer.normalize, which takes its term attribute from attributeFactory(fieldName), and ICUCollationKeyAnalyzer gives its collation attribute factory only to its KeywordTokenizer and does not override attributeFactory, so normalize("b") is the UTF-8 byte [62] while the index holds the ICU collation key [2d 1 5 1 5 0]. With other inputs the same range both misses documents inside it and matches documents outside it.

## Expected vs actual

- Expected: `[a TO c]` matches the document `b`: 1 hit.
- Actual: 0 hits: `AssertionError: expected:<1> but was:<0>`, with assertions on and off.

## Files

- [src/org/apache/lucene/analysis/icu/TestICUCollationKeyAnalyzerRange.java](src/org/apache/lucene/analysis/icu/TestICUCollationKeyAnalyzerRange.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/analysis/icu/build/libs/lucene-analysis-icu-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.icu.TestICUCollationKeyAnalyzerRange
```

The module(s) analysis-icu, facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestICUCollationKeyAnalyzerRange.java](TestICUCollationKeyAnalyzerRange.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
