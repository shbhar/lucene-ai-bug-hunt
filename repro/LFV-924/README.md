# SimpleQueryParser.parse throws StackOverflowError on deeply nested parentheses, because parseSubQuery and consumeSubQuery recurse once per '(' with no depth bound, although the parser is meant to do its best with any human-entered text

Bug LFV-924 · Area: queryparser / simple (SimpleQueryParser.parseSubQuery / consumeSubQuery recursion) · Priority: low · Impact: wrong error · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give SimpleQueryParser, the parser meant for raw human-entered text, a query with many balanced nested groups, for example "(".repeat(d) + "a" + ")".repeat(d), and expect a Query: its class javadoc says a person can type whatever they want and the parser will do its best to interpret it no matter how poorly composed, parse(String) declares no exception, and at small depths the same input parses to the plain TermQuery f:a. Instead parseSubQuery calls consumeSubQuery for every '(' and consumeSubQuery calls parseSubQuery again on the inner text, one frame pair per level with no depth bound, so a few thousand to about 25,000 levels (about 500 on a 256 KB stack), depending on stack size and JIT state, exhaust the thread stack and java.lang.StackOverflowError escapes parse. The clause limit does not help, because the nested groups collapse to a single clause.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: parseSubQuery calls consumeSubQuery for every '(' and consumeSubQuery calls parseSubQuery again on the inner text, one frame pair per level with no depth bound, so a few thousand to about 25,000 levels (about 500 on a 256 KB stack), depending on stack size and JIT state, exhaust the thread stack and java.lang.StackOverflowError escapes parse.

## Files

- [src/org/apache/lucene/queryparser/simple/TestSimpleDeepNesting.java](src/org/apache/lucene/queryparser/simple/TestSimpleDeepNesting.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed 364DEE9
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=364DEE9 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queryparser.simple.TestSimpleDeepNesting
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
