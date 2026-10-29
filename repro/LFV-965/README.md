# XML query parser: CoreParser.getQuery(Element) and getSpanQuery(Element) on any JDK, and plain CoreParser.parse(InputStream) on JDK 21 (the 10.x minimum, including the 10.6.0 RC), throw StackOverflowError instead of the declared ParserException on deeply nested queries (on a 1 MB thread stack about 3,000 nesting levels: about 180 KB of nested BooleanQuery XML or 51 KB of nested SpanOr; every release since 1.9.1)

Bug LFV-965 · Area: queryparser / xml (CoreParser, QueryBuilderFactory and SpanQueryBuilderFactory with the compound and span builders, DOMUtils) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give the XML query parser a well-formed query whose elements nest a few thousand levels deep, for example &lt;BooleanQuery&gt;&lt;Clause occurs="must"&gt; repeated 3,000 times around one &lt;TermQuery&gt; (about 180 KB; enough on a 1 MB thread stack, while the 2 MB aarch64 default needs 5,000 or more levels), which the shipped DTD allows ('queries/filters of an arbitrary depth and complexity') and which parses fine at 40 levels, and you expect a Query or the ParserException that CoreParser.parse(InputStream), getQuery(Element) and getSpanQuery(Element) declare. Instead java.lang.StackOverflowError escapes, because each compound builder (BooleanQuery, DisjunctionMaxQuery, ConstantScoreQuery and the span builders) calls back into QueryBuilderFactory or SpanQueryBuilderFactory once per nesting level and nothing counts the depth or turns the Error into a ParserException. Whether the plain text path fails depends on the JDK, not on Lucene: JDK 21, the minimum for 10.x including the 10.6.0 RC, has no XML element-depth limit by default and overflows, while JDK 25 (main's minimum) rejects more than 100 nested elements with a ParserException; the DOM entries overflow on every JDK.

## Expected vs actual

- Expected: You expect a Query or the ParserException that CoreParser.parse(InputStream), getQuery(Element) and getSpanQuery(Element) declare.
- Actual: java.lang.StackOverflowError escapes, because each compound builder (BooleanQuery, DisjunctionMaxQuery, ConstantScoreQuery and the span builders) calls back into QueryBuilderFactory or SpanQueryBuilderFactory once per nesting level and nothing counts the depth or turns the Error into a ParserException.

## Files

- [src/org/apache/lucene/queryparser/xml/TestXmlQueryParserDeepNesting.java](src/org/apache/lucene/queryparser/xml/TestXmlQueryParserDeepNesting.java) (JUnit test)

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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queryparser.xml.TestXmlQueryParserDeepNesting
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
