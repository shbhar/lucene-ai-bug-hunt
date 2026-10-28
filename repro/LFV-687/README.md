# Surround query parser keeps the escaping backslashes inside a quoted term, so "a\"b" searches for a\"b instead of a"b and "c:\\temp dir" searches for c:\\temp dir instead of c:\temp dir

Bug LFV-687 · Area: queryparser / surround (QueryParser.jj QUOTED token and SimpleTerm production, SrndTermQuery) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-687.html](../../bugs/LFV-687.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse a surround query with a quoted term that escapes a double quote or a backslash, such as "a\"b" or "c:\\temp dir", and run it on a field that holds the untokenized value a"b or c:\temp dir. You expect a match, because the grammar comment on the QUOTED token says 'escape quoted quote or backslash by backslash' and the lexer accepts a backslash inside quotes only as such an escape. Instead the parser strips only the outer quotes, so SrndTermQuery searches for a\"b and c:\\temp dir with the backslashes kept: 0 hits, or a silent match on a different document that holds that escaped text, and no surround syntax matches those terms exactly.

## Expected vs actual

- Expected: `"a\"b"` matches doc quote (`a"b`) and `"c:\\temp dir"` matches doc path (`c:\temp dir`).
- Actual: They match doc raw-quote (`a\"b`) and doc raw-path (`c:\\temp dir`) instead; without those documents, 0 hits.

## Files

- [src/org/apache/lucene/queryparser/surround/query/TestSrndQuotedTermEscapes.java](src/org/apache/lucene/queryparser/surround/query/TestSrndQuotedTermEscapes.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queryparser.surround.query.TestSrndQuotedTermEscapes
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestSurroundQuotedTermEscapes.java](TestSurroundQuotedTermEscapes.java)
- [TestSrndQuotedTermEscapes.java](TestSrndQuotedTermEscapes.java)
- [TestSurroundQuotedEscapesV1.java](TestSurroundQuotedEscapesV1.java)
- [TestSrndQuotedEscapesV3.java](TestSrndQuotedEscapesV3.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
