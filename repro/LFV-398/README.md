# Flexible query parser: EscapeQuerySyntaxImpl does not escape '@' after the first character of a term or a term that is exactly &amp;&amp; or ||, so QueryNode#toQueryString output for such terms does not parse

Bug LFV-398 · Area: queryparser / flexible parser term escaping (EscapeQuerySyntaxImpl, as used by QueryNode#toQueryString) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

Since 9.1 the standard flexible parser reads an '@' anywhere in a term as syntax (it must be escaped, as in user\@example.com), and &amp;&amp; and || are its AND and OR operators, so QueryNode#toQueryString, documented to return 'a query string understood by the query parser', has to escape them. Its escaper EscapeQuerySyntaxImpl escapes '@' only when it is the first character of a term and lists AND, OR and NOT but not &amp;&amp; or || as reserved words, so the node for 'user\@example.com' prints 'df:user@example.com' and the nodes for '\&amp;&amp;' and '\||' print 'df:&amp;&amp;' and 'df:||'. Parsing any of these strings again throws INVALID_SYNTAX_CANNOT_PARSE; with a two-line fix to the escaper's tables every printable-ASCII term of length 1-3 round-trips.

The test shows that flexible `QueryNode#toQueryString(new EscapeQuerySyntaxImpl())` does not escape an inner `@` (`user\@example.com`) or the term `&&` (`\&&`), so the printed query string does not parse.

## Expected vs actual

Expected: `df:user\@example.com` and `df:\&&` (both parse back). Actual: `df:user@example.com` and `df:&&` (INVALID_SYNTAX_CANNOT_PARSE when re-parsed).

## Files

- [src/org/apache/lucene/queryparser/flexible/standard/parser/TestEscapeQuerySyntaxRoundTrip.java](src/org/apache/lucene/queryparser/flexible/standard/parser/TestEscapeQuerySyntaxRoundTrip.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 2 test(s) run, 2 failed.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queryparser.flexible.standard.parser.TestEscapeQuerySyntaxRoundTrip
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/queryparser-flexible-simple-xml/](../../models/queryparser-flexible-simple-xml/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
