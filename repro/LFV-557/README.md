# UkrainianMorfologikAnalyzer overrides neither normalize nor initReaderForNormalization, so prefix, wildcard, regexp, range and fuzzy terms from the query parsers skip its lower-casing and its apostrophe, stress-mark, soft-hyphen and ґ mapping and miss words spelled exactly as in the document

Bug LFV-557 · Area: analysis-morfologik / language analyzers (UkrainianMorfologikAnalyzer normalization: Analyzer#normalize and initReaderForNormalization, used by the query parsers for multi-term queries) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-557.html](../../bugs/LFV-557.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index Ukrainian text with UkrainianMorfologikAnalyzer, which maps typographic apostrophes to ', removes stress marks and soft hyphens, maps ґ to г and lower-cases every token before lemmatizing, so the document 'м’ясо Lucene' is stored as the terms м'ясо and lucene. A prefix, wildcard, regexp, range or fuzzy query built by a query parser sends its term through Analyzer.normalize, documented to return 'the representation that it would have in the index', but this analyzer overrides neither normalize(String, TokenStream) nor initReaderForNormalization, so normalize returns the text unchanged. As a result 'м’яс*', 'Luc*', 'Luce?e' and '[Lucene TO Lucene]' find nothing while "м'яс*" and 'luc*' find the document, and normalize("Lucene") returns 'Lucene' although the index holds 'lucene'.

## Expected vs actual

- Expected: `normalize("Lucene")` returns `lucene`, and `м’яс*` and `Luc*` each find the document `м’ясо Lucene` (1 hit).
- Actual: `normalize` returns all five words unchanged and both prefixes find 0 hits, while the controls `м'яс*` and `luc*` find 1.

## Files

- [src/org/apache/lucene/analysis/uk/TestUkrainianAnalyzerNormalize.java](src/org/apache/lucene/analysis/uk/TestUkrainianAnalyzerNormalize.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/morfologik/build/libs/lucene-analysis-morfologik-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.uk.TestUkrainianAnalyzerNormalize
```

The module(s) analysis-morfologik, facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestUkrainianNormalize.java](TestUkrainianNormalize.java)
- [TestUkrainianAnalyzerNormalize.java](TestUkrainianAnalyzerNormalize.java)
- [testukrainiananalyzer-test.diff](testukrainiananalyzer-test.diff)
- [TestUkrainianNormalizeRuleProbe.java](TestUkrainianNormalizeRuleProbe.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
