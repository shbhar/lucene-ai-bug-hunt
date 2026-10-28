# Flexible StandardQueryParser and PrecedenceQueryParser throw StackOverflowError from the processor phase, instead of the documented QueryNodeException, on deeply nested groups that the syntax phase accepts (about 1,550-2,000 nested '+(...)^2' groups on a 1 MB thread stack, 3,300-7,100 on the 2 MB aarch64 default stack, once the JIT has warmed up)

Bug LFV-927 · Area: queryparser / flexible (QueryParserHelper three-phase parse: QueryNodeProcessorImpl and QueryTreeBuilder recursion) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse valid query text with many nested groups, for example "+(".repeat(d) + "a" + ")^2".repeat(d), with the flexible StandardQueryParser.parse(query, field) or PrecedenceQueryParser, which document '@throws QueryNodeException if something wrong happens along the three phases', and you expect a Query or a QueryNodeException; the syntax phase (StandardSyntaxParser) accepts the text, and it already turns any Error, including a StackOverflowError of its own, into QueryNodeParseException. Instead the processor phase recurses once per query node with no depth bound and no guard (QueryNodeProcessorImpl.processIteration -&gt; processChildren -&gt; processIteration; each '+(...)^2' level makes three nodes), the thread runs out of stack, and java.lang.StackOverflowError escapes parse, so a shallower query gets a JVM error where a much deeper one can get the documented exception.

## Expected vs actual

- Expected: You expect a Query or a QueryNodeException; the syntax phase (StandardSyntaxParser) accepts the text, and it already turns any Error, including a StackOverflowError of its own, into QueryNodeParseException.
- Actual: The processor phase recurses once per query node with no depth bound and no guard (QueryNodeProcessorImpl.processIteration -&gt; processChildren -&gt; processIteration; each '+(...)^2' level makes three nodes), the thread runs out of stack, and java.lang.StackOverflowError escapes parse, so a shallower query gets a JVM error where a much deeper one can get the documented exception.

## Files

- [src/org/apache/lucene/queryparser/flexible/standard/TestFlexibleDeepNestingPhases.java](src/org/apache/lucene/queryparser/flexible/standard/TestFlexibleDeepNestingPhases.java) (JUnit test)

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
     execute --select-class org.apache.lucene.queryparser.flexible.standard.TestFlexibleDeepNestingPhases
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
