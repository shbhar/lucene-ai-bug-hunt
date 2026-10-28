# Surround QueryParser.parse throws StackOverflowError instead of its declared ParseException on deeply nested groups (as few as about 740 levels, about 1.5 KB of query text, on a 1 MB thread stack)

Bug LFV-919 · Area: queryparser / surround (generated JavaCC parser, QueryParser.jj) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call the surround parser's documented entry point QueryParser.parse(String), which declares only ParseException and already turns the lexer's TokenMgrError (an Error) into a ParseException, on a valid query with many nested groups, for example "(".repeat(d) + "a" + ")".repeat(d) or a long chain of "(a OR " groups, and expect a query or a ParseException. Instead the generated recursive-descent grammar, which spends seven frames per "(" (FieldsQuery -&gt; OrQuery -&gt; AndQuery -&gt; NotQuery -&gt; NQuery -&gt; WQuery -&gt; PrimaryQuery -&gt; FieldsQuery) and has no depth bound, runs out of stack and java.lang.StackOverflowError escapes parse, so a caller that handles ParseException gets an undeclared Error. On a 1 MB thread stack as few as about 740 nested groups (1,473 chars) are enough on the first deep parse, and an 800-term chain of parenthesised binary ORs (7,884 chars, under the module's 1024-basic-query limit) fails even after JIT warm-up.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The generated recursive-descent grammar, which spends seven frames per "(" (FieldsQuery -&gt; OrQuery -&gt; AndQuery -&gt; NotQuery -&gt; NQuery -&gt; WQuery -&gt; PrimaryQuery -&gt; FieldsQuery) and has no depth bound, runs out of stack and java.lang.StackOverflowError escapes parse, so a caller that handles ParseException gets an undeclared Error.

## Files

- [src/org/apache/lucene/queryparser/surround/parser/TestSurroundDeepNesting.java](src/org/apache/lucene/queryparser/surround/parser/TestSurroundDeepNesting.java) (JUnit test)

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
     execute --select-class org.apache.lucene.queryparser.surround.parser.TestSurroundDeepNesting
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
