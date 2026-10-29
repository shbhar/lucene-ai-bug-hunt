# Classic QueryParser with the default splitOnWhitespace=false: since #16446 an AND/OR that directly follows a token the analyzer drops (such as a stopword) binds to the whole preceding run of plain terms instead of the last term, so typing the stopword changes which documents match (unreleased regression on main and branch_10_6)

Bug LFV-284 · Area: queryparser / classic QueryParser and MultiFieldQueryParser (QueryParser.jj MultiTerm run grouping, QueryParserBase.addClause conjunction step) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-284.html](../../bugs/LFV-284.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse 'apple banana the AND cherry' with the classic QueryParser at its default settings and an analyzer that drops 'the', and expect the same query as for 'apple banana AND cherry', namely body:apple +body:banana +body:cherry, which is what Lucene 10.5.2 returns and what main returns with splitOnWhitespace=true. On main you get +(body:apple body:banana) +body:cherry, so a document containing only 'apple cherry' now matches; with the AND default operator, 'apple banana the OR cherry' becomes (+body:apple +body:banana) body:cherry instead of +body:apple body:banana body:cherry, so a document with only 'cherry' matches and one with only 'apple' no longer does. The cause is #16446, which now adds the analysed run 'apple banana' as one clause: the stopword yields no clause, so the following conjunction's 'make the preceding clause required (or optional)' step in addClause changes the whole run instead of 'banana'.

The test shows that with the classic QueryParser's defaults, a stopword between a run of plain terms and AND changes the parsed query (since #16446; 10.5.2 parses both strings the same).

## Expected vs actual

Expected `apple banana the AND cherry` to parse like `apple banana AND cherry` (`body:apple +body:banana +body:cherry`); actual: `+(body:apple body:banana) +body:cherry`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/queryparser/classic/TestStopwordBeforeAnd.java](src/org/apache/lucene/queryparser/classic/TestStopwordBeforeAnd.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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
     execute --select-class org.apache.lucene.queryparser.classic.TestStopwordBeforeAnd
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/queryparser-classic/](../../models/queryparser-classic/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
