# SmartChineseAnalyzer.normalize only lower-cases, but indexing folds full-width Latin letters and digits to half-width (in the tokenizer's SegTokenFilter), so full-width prefix, wildcard, regexp, range and fuzzy terms from the query parsers miss documents that the same word as a term query finds

Bug LFV-565 · Area: analysis-smartcn / language analyzers (SmartChineseAnalyzer normalization: Analyzer#normalize vs the width and case folding inside HMMChineseTokenizer's SegTokenFilter, used by the query parsers for multi-term queries) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-565.html](../../bugs/LFV-565.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index the text '我们使用Ｌｕｃｅｎｅ' (or '我们使用Lucene') with the stock SmartChineseAnalyzer and search with the classic QueryParser: the term query 'Ｌｕｃｅｎｅ' and the half-width prefix 'Luc*' find the document, so you expect the full-width prefix 'Ｌｕｃ*' to find it too. Instead it finds 0 documents, with no error: the tokenizer's SegTokenFilter folds full-width Latin letters and digits to half-width and lower-cases A-Z, so the index holds 'lucen', but SmartChineseAnalyzer.normalize, which the query parsers apply to prefix, wildcard, regexp, range and fuzzy terms, is only a LowerCaseFilter, so the query term stays full-width ('ｌｕｃ*'). Likewise Analyzer.normalize returns 'ａｂｃ' for 'ＡＢＣ' and '１２３' for '１２３' (the index holds 'abc' and '123') and 'é' for 'É' (the index keeps 'É').

The test shows that `SmartChineseAnalyzer` indexes the full-width word `ＡＢＣ` as `abc`, but `normalize("f", "ＡＢＣ")` (used by the query parsers for prefix, wildcard, regexp, range and fuzzy terms) keeps it full-width.

## Expected vs actual

Expected: `normalize` returns the indexed term `abc`. Actual: `ａｂｃ`.

## Files

- [src/org/apache/lucene/analysis/cn/smart/TestSmartChineseNormalizeFullWidth.java](src/org/apache/lucene/analysis/cn/smart/TestSmartChineseNormalizeFullWidth.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/smartcn/build/libs/lucene-analysis-smartcn-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.cn.smart.TestSmartChineseNormalizeFullWidth
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
