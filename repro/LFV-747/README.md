# Flexible StandardQueryParser: term literals inside fn: interval functions keep their escape backslashes, so fn:or(a\:b) searches for the text a\:b instead of the term a:b

Bug LFV-747 · Area: queryparser / flexible StandardQueryParser interval functions (StandardSyntaxParser.jj IntervalText and IntervalFuzzyTerm, intervalfn AnalyzedText and FuzzyTerm) · Priority: medium · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse fn:or(a\:b) (or fn:ordered(a\:b c), fn:fuzzyTerm(a\:b 0), fn:or("a\"b")) with StandardQueryParser and expect it to find the documents that hold a:b (or a"b), because the parser's javadoc says 'any character can be quoted with a backslash' and the same escaped literal outside fn: is parsed as the term a:b. Instead the IntervalText and IntervalFuzzyTerm grammar productions pass the raw token text, backslashes included, to AnalyzedText and FuzzyTerm, so the interval query searches for the text a\:b. With WhitespaceAnalyzer it misses the a:b document and matches a document that holds a\:b; with StandardAnalyzer it becomes BLOCK(a,b) and matches 'a b c'; no error is raised.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The IntervalText and IntervalFuzzyTerm grammar productions pass the raw token text, backslashes included, to AnalyzedText and FuzzyTerm, so the interval query searches for the text a\:b.

## Files

- [src/org/apache/lucene/queryparser/flexible/standard/TestIntervalFunctionTermEscapes.java](src/org/apache/lucene/queryparser/flexible/standard/TestIntervalFunctionTermEscapes.java) (JUnit test)

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
     execute --select-class org.apache.lucene.queryparser.flexible.standard.TestIntervalFunctionTermEscapes
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
