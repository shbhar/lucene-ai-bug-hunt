# Classic QueryParser and flexible StandardQueryParser: the query syntax docs never say that only space, TAB, LF, CR and U+3000 separate clauses, so AND, OR or '-' written next to any other Unicode space (EN, EM or THIN space, U+205F, NBSP,...) silently becomes part of a search term and a field prefix spreads to the following words: 'title:foo&lt;EM SPACE&gt;AND&lt;EM SPACE&gt;-bar' parses as 'title:foo title:and title:bar'

Bug LFV-847 · Area: queryparser / classic and flexible standard grammars (_WHITESPACE, _TERM_START_CHAR) and the query syntax documentation · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse 'title:foo AND -bar' with the classic or the flexible query parser, but the spaces are EM SPACEs (U+2003), as in text pasted from a formatted document; nothing in the query syntax docs says which characters separate clauses, so you expect the same query as with ordinary spaces, '+title:foo -body:bar'. Instead both grammars separate clauses only on space, TAB, LF, CR and U+3000, so 'foo&lt;EM&gt;AND&lt;EM&gt;-bar' is one term that the analyzer splits under the first field: the result is 'title:foo title:and title:bar', AND became the search word 'and', the '-' exclusion is gone and a document whose body contains bar now matches (2 hits instead of 1). The closed separator set is very likely intended, so the defect is the missing documentation; when the next clause has its own field prefix ('-body:bar') both parsers throw ParseException instead.

## Expected vs actual

- Expected: You expect the same query as with ordinary spaces, '+title:foo -body:bar'.
- Actual: Both grammars separate clauses only on space, TAB, LF, CR and U+3000, so 'foo&lt;EM&gt;AND&lt;EM&gt;-bar' is one term that the analyzer splits under the first field: the result is 'title:foo title:and title:bar', AND became the search word 'and', the '-' exclusion is gone and a document whose body contains bar now matches (2 hits instead of 1).

## Files

- [src/org/apache/lucene/queryparser/classic/TestUnicodeSpaceBetweenClauses.java](src/org/apache/lucene/queryparser/classic/TestUnicodeSpaceBetweenClauses.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queryparser.classic.TestUnicodeSpaceBetweenClauses
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
