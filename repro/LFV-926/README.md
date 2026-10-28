# Classic QueryParser.parse (and its MultiFieldQueryParser, ComplexPhraseQueryParser and ExtendableQueryParser subclasses) throws StackOverflowError instead of the documented ParseException on deeply nested groups (about 2,650-3,700 levels, 5-7 KB of query text, on a 1 MB thread stack)

Bug LFV-926 · Area: queryparser / classic (generated JavaCC parser QueryParser.jj and QueryParserBase.parse) · Priority: low · Impact: wrong error · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse a valid query with many nested groups, for example "(".repeat(d) + "a" + ")".repeat(d), with the classic QueryParser.parse(String), which documents '@throws ParseException if the parsing fails' and already turns the generated lexer's TokenMgrError (a java.lang.Error) and clause-limit hits into ParseException, and you expect the query (here just f:a) or a ParseException. Instead the generated recursive-descent grammar calls Query -&gt; Clause -&gt; Query once per '(' with no depth bound, the thread runs out of stack, and java.lang.StackOverflowError escapes parse. On a cold JVM about 2,650 levels (5,331 chars) are enough at -Xss1m and about 5,900 on the 2 MB aarch64 default stack, while the same 100,000-level input parses to f:a on a 256 MB stack.

## Expected vs actual

- Expected: You expect the query (here just f:a) or a ParseException.
- Actual: The generated recursive-descent grammar calls Query -&gt; Clause -&gt; Query once per '(' with no depth bound, the thread runs out of stack, and java.lang.StackOverflowError escapes parse.

## Files

- [src/org/apache/lucene/queryparser/classic/TestClassicDeepNesting.java](src/org/apache/lucene/queryparser/classic/TestClassicDeepNesting.java) (JUnit test)

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
     execute --select-class org.apache.lucene.queryparser.classic.TestClassicDeepNesting
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
