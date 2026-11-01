# CustomAnalyzer.normalize skips the token filters inside a when()/whenTerm() block or a ProtectedTermFilterFactory (ConditionalTokenFilterFactory has no normalize()), so query-parser range, fuzzy, prefix, wildcard and regexp terms are not lower-cased or folded like the indexed term and silently miss documents

Finding LFV-1063 · Area: analysis-common / CustomAnalyzer conditional blocks (ConditionalTokenFilterFactory, ProtectedTermFilterFactory) and Analyzer.normalize, which the query parsers use for multi-term queries · Priority: medium · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1063.html](../../bugs/LFV-1063.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a CustomAnalyzer whose lowercase filter sits inside a conditional block (withTokenizer("whitespace").whenTerm(t -&gt; t.length() &gt; 2).addTokenFilter("lowercase").endwhen(), or a ProtectedTermFilterFactory wrapping lowercase as in that factory's javadoc), index 'Lucene' (stored as the term 'lucene') and search with the classic QueryParser range [Lucene TO Lucene]. You expect Analyzer.normalize("f", "Lucene") to return 'lucene', 'the representation that it would have in the index', and the query to find the document, as both do for the same chain without the condition. Instead normalize returns 'Lucene' unchanged and the query finds 0 documents with no error, and Apa*, Apach?, APACHE~1 and /Apa.*/ miss 'Apache' the same way, because ConditionalTokenFilterFactory overrides create() but not normalize(), so CustomAnalyzer.normalize never reaches the normalize() of the wrapped filters.

## Expected vs actual

- Expected: `analyzer.normalize("f", "Lucene")` returns `lucene`, the term the index holds for `Lucene`.
- Actual: It returns `Lucene` unchanged: `ComparisonFailure: expected:<[l]ucene> but was:<[L]ucene>`, with assertions on and off.

## Files

- [src/org/apache/lucene/analysis/custom/TestConditionalFilterNormalize.java](src/org/apache/lucene/analysis/custom/TestConditionalFilterNormalize.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.custom.TestConditionalFilterNormalize
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestConditionalFilterNormalize.java](TestConditionalFilterNormalize.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
