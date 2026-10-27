# Flexible query parser: IntervalQueryNode#toQueryString prints some fn: interval functions in a form the parser rejects or reads as a different query (fn:fuzzyTerm(abc) prints as fn:fuzzyTerm(abc 2128))

Bug LFV-748 · Area: queryparser / flexible StandardQueryParser interval function nodes (IntervalQueryNode#toQueryString; intervalfn FuzzyTerm, Wildcard and AnalyzedText toString) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse fn:fuzzyTerm(abc), fn:wildcard(ab* 5) or fn:or("a:b" c) with the flexible syntax parser, print the node tree with toQueryString, and expect a string that StandardQueryParser reads back as the same query, as the flexible parser's package documentation promises ('a query string that can be parsed back by the original text parser'). Instead FuzzyTerm prints maxEdits and maxExpansions with no space between them (fn:fuzzyTerm(abc 2128), which re-parses as maxEdits 2128 and throws 'max edits must be 0..2'), Wildcard prints 'maxExpansions:5', a syntax error, and AnalyzedText quotes a literal only when it contains ASCII whitespace, so fn:or("a:b" c) prints the unparsable fn:or(a:b c). A quoted literal with the ideographic space U+3000 prints unquoted and re-parses, with no error, as two separate literals.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: FuzzyTerm prints maxEdits and maxExpansions with no space between them (fn:fuzzyTerm(abc 2128), which re-parses as maxEdits 2128 and throws 'max edits must be 0..2'), Wildcard prints 'maxExpansions:5', a syntax error, and AnalyzedText quotes a literal only when it contains ASCII whitespace, so fn:or("a:b" c) prints the unparsable fn:or(a:b c).

## Files

- [src/org/apache/lucene/queryparser/flexible/standard/TestIntervalNodeToQueryString.java](src/org/apache/lucene/queryparser/flexible/standard/TestIntervalNodeToQueryString.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
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
     execute --select-class org.apache.lucene.queryparser.flexible.standard.TestIntervalNodeToQueryString
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
