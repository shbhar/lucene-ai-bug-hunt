# MultiFieldQueryParser under AND treats clause i of every field's analyzed BooleanQuery as term position i and requires each group, so fields whose analyzers tokenize the same text differently lose (and sometimes gain wrong) matches (released since 6.2.0, default settings since 7.0.0), and since #16446 (unreleased) graph-phrase alternatives such as a multi-word synonym are required together

Bug LFV-319 · Area: queryparser / classic MultiFieldQueryParser (getFieldQuery(String,String,boolean) per-field clause grouping and getMultiTermPositionQuery; core util/QueryBuilder graph phrases) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-319.html](../../bugs/LFV-319.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search the fields title and body with MultiFieldQueryParser under AND_OPERATOR and expect what its class javadoc promises: every query term must appear, in any field, so adding a field never loses a document that QueryParser on one of those fields finds. Instead getFieldQuery takes clause i of each field's analyzed BooleanQuery as 'term position i' and requires every such group: with title tokenized on whitespace and body on letters, 'wi-fi router' becomes +(title:wi-fi body:wi) +(title:router body:fi) +(body:router), which misses a document titled 'wi-fi router' and matches one with title 'router' and body 'wi router' (released since 6.2.0, default settings since 7.0.0; since #16446 the single word 'wi-fi' loses the match too). Since #16446 (unreleased), with autoGeneratePhraseQueries and the multi-word synonym 'guinea pig' -&gt; 'cavy', 'guinea-pig' becomes +(body:"guinea pig") +(body:cavy), so even MultiFieldQueryParser over body alone finds nothing where QueryParser(body) finds the document.

## Expected vs actual

- Expected: `wi-fi router` finds doc 0 only, `wi-fi` finds doc 0, and `guinea-pig` finds the guinea-pig document, as `QueryParser` does.
- Actual: `wi-fi router` finds doc 1 instead of doc 0 (also on 10.5.2 and 9.12.3); `wi-fi` and `guinea-pig` find nothing on main.

## Files

- [src/org/apache/lucene/queryparser/classic/TestMultiFieldQueryParserPositionGroups.java](src/org/apache/lucene/queryparser/classic/TestMultiFieldQueryParserPositionGroups.java) (JUnit test)

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
     execute --select-class org.apache.lucene.queryparser.classic.TestMultiFieldQueryParserPositionGroups
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestMultiFieldQueryParserClauseGroups.java](TestMultiFieldQueryParserClauseGroups.java)
- [fix-b.diff](fix-b.diff)

Formal models and scripts this bug's part of the hunt used: [../../models/queryparser-classic/](../../models/queryparser-classic/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
