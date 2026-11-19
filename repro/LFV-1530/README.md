# Flexible StandardQueryParser passes the empty text that marks an open range bound through Analyzer.normalize, so with an analyzer whose normalization of the empty string is not empty a one-sided range such as [abc TO *] becomes a closed range and can match the wrong documents (none in every executed case where the analyzer's term for the empty string sorts before every indexed term); every release from 7.0.0 to 10.6.0, branch_11x and main

Finding LFV-1530 · Area: queryparser / flexible StandardQueryParser (term range bounds: TermRangeQueryNodeProcessor after OpenRangeQueryNodeProcessor) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1530.html](../../bugs/LFV-1530.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse the one-sided range "[abc TO *]" with the flexible StandardQueryParser and an analyzer whose Analyzer.normalize writes "collated" + term as text (the shape of the mock collation analyzer in Lucene's own query-parser tests; it keeps the default term attribute). You expect field:[collatedabc TO *], which the classic QueryParser builds with the same analyzer; instead you get field:[collatedabc TO collated], a closed range that matches no document, with no error, because the parser marks the open bound with an empty text and then passes that empty text through Analyzer.normalize like a real bound. Only analyzers that turn the empty string into a non-empty term are affected (application analyzers that write a sort key or a marker as text; among the stock analyzer classes the java.text CollationKeyAnalyzer, with which "[a TO *]" on a field that holds b returns 0 documents instead of 1, a combination that a source comment already limits for another reason, its binary terms; a CustomAnalyzer built from stock parts can be configured into one); every release since 7.0.0 does it.

## Expected vs actual

- Expected: `parse("[abc TO *]", "field")` returns `field:[collatedabc TO *]`, as the classic `QueryParser` does with the same analyzer.
- Actual: It returns `field:[collatedabc TO collated]`: `java.lang.AssertionError: expected:<field:[collatedabc TO *]> but was:<field:[collatedabc TO collated]>`.

## Files

- [src/org/apache/lucene/queryparser/flexible/standard/TestOpenRangeBoundWithNormalizingAnalyzer.java](src/org/apache/lucene/queryparser/flexible/standard/TestOpenRangeBoundWithNormalizingAnalyzer.java) (JUnit test)

## Run it

- Tested on Lucene main 1cfa86310f76 (2026-11-17): still reproduces, with assertions on and off.
- Tested on Lucene branch_11x 89e68e0f3a50 (2026-11-17): reproduced, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene main 9965d34e3c77 (2026-11-16): reproduced, with assertions on and off.
- Tested on Lucene branch_11x 9d2b5a15427a (2026-11-16): reproduced, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- What each build does: the test fails the same way on Lucene main, on the 11.0 release branch (branch_11x) and with the 10.6.0 release jars, with and without assertions; the finding's record lists every release from 7.0.0 on. What the test does: it defines an analyzer that keeps the default term attribute and writes "collated" + term, at index time and in normalize, parses [abc TO *] with new StandardQueryParser(analyzer), and expects the query with an open upper bound that TermRangeQuery.newStringRange("field", "collatedabc", null, true, true) gives and that the classic QueryParser builds from the same text. It fails with java.lang.AssertionError: expected:&lt;field:[collatedabc TO *]&gt; but was:&lt;field:[collatedabc TO collated]&gt;: the upper bound is the analyzer's term for the empty string. It needs an analyzer of this kind: with one that normalizes the empty string to the empty string the bound stays open (the finding page has those runs). The test builds no index and draws no random value, and it failed the same way with every seed that was run. On a checkout of the 11.0 branch set V=11.0.0-SNAPSHOT; for the release, put the seven 10.6.0 jars from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework, lucene-facet, lucene-queries, lucene-queryparser, lucene-sandbox) in place of the seven $LUCENE jars. For the Gradle alternative at the end of this page the module is lucene/queryparser; without -Ptests.asserts the build of main and of branch_11x draws the assertion setting from its seed, and the build of the 10.6.0 sources runs with assertions on; the test fails with either setting.
- This finding was re-run on Lucene main ec3b3cca8f10 on 2026-11-18: still reproduces.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.AssertionError: expected:&lt;field:[collatedabc TO *]&gt; but was:&lt;field:[collatedabc TO collated]&gt;.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed 0A7E0834
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=0A7E0834 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queryparser.flexible.standard.TestOpenRangeBoundWithNormalizingAnalyzer
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestOpenRangeBoundWithNormalizingAnalyzer.java](TestOpenRangeBoundWithNormalizingAnalyzer.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
