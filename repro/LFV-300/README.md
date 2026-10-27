# Classic QueryParser and MultiFieldQueryParser: a negative phrase slop such as "a b"~-1 makes parse throw IllegalArgumentException instead of ParseException

Bug LFV-300 · Area: queryparser / classic (QueryParserBase.handleQuotedTerm and getFieldQuery, MultiFieldQueryParser.applySlop; the negative-slop check of PhraseQuery and MultiPhraseQuery.Builder) · Priority: low · Impact: wrong error · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse "a b"~-1, a quoted phrase with a negative proximity, with the classic QueryParser or MultiFieldQueryParser. You expect a ParseException, which parse documents and which the same parser gives for the negative fuzzy distance a~-1 (or the slop to be ignored, as SimpleQueryParser does). Instead parse throws IllegalArgumentException 'Slop must be &gt;= 0, got -1' from the PhraseQuery constructor ('slop value cannot be negative' from MultiPhraseQuery.Builder when the analyzer adds synonyms), because the lexer accepts '~-1' and handleQuotedTerm passes the negative number on unchecked.

## Expected vs actual

- Expected: You expect a ParseException, which parse documents and which the same parser gives for the negative fuzzy distance a~-1 (or the slop to be ignored, as SimpleQueryParser does).
- Actual: parse throws IllegalArgumentException 'Slop must be &gt;= 0, got -1' from the PhraseQuery constructor ('slop value cannot be negative' from MultiPhraseQuery.Builder when the analyzer adds synonyms), because the lexer accepts '~-1' and handleQuotedTerm passes the negative number on unchecked.

## Files

- [src/org/apache/lucene/queryparser/classic/TestNegativePhraseSlop.java](src/org/apache/lucene/queryparser/classic/TestNegativePhraseSlop.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.queryparser.classic.TestNegativePhraseSlop
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
