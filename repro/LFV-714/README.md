# ComplexPhraseQueryParser: a quoted phrase that is only one parenthesized group is read as a sequence of positions, so "(john OR jonathan)" matches only the sequence 'john jonathan' and "(jo* -john)" matches documents whose only jo* term is john

Bug LFV-714 · Area: queryparser / complexPhrase: ComplexPhraseQueryParser (ComplexPhraseQuery.parsePhraseElements second-pass parse, read by ComplexPhraseQuery.rewrite) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-714.html](../../bugs/LFV-714.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse "(john OR jonathan)" or "(jo* -john)" with ComplexPhraseQueryParser and search; inside a complex phrase a bracketed group is one position with alternatives (class javadoc example "(john jon jonathan~) peters*", upstream tests "(john OR johathon) smith" and "(jo* -john) smith") and one-element phrases are supported, so you expect the documents that contain john or jonathan, or a jo* term other than john. Instead the classic grammar drops parentheses around the whole input (QueryParser.jj:289-293 and:311), so the second pass gets the same BooleanQuery as for "john OR jonathan", and ComplexPhraseQuery.rewrite makes each member a consecutive position: "(john OR jonathan)" becomes spanNear([john, jonathan], 0, true) and matches only documents that contain the sequence 'john jonathan', and "(jo* -john)" becomes spanNot(spanOr(jo*), spanNear([spanOr(jo*), john])) and also matches 'john smith', whose only jo* term is john.

## Expected vs actual

- Expected: `"(john OR jonathan)"` and `"(jo* -john)"` each return 4 documents, as one position with alternatives, like the control.
- Actual: `"(john OR jonathan)"` returns 1, "john jonathan" (rewritten `spanNear([john, jonathan])`), and `"(jo* -john)"` returns all 5, including "john smith".

## Files

- [src/org/apache/lucene/queryparser/complexPhrase/TestComplexPhraseLoneGroup.java](src/org/apache/lucene/queryparser/complexPhrase/TestComplexPhraseLoneGroup.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queryparser.complexPhrase.TestComplexPhraseLoneGroup
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestComplexPhraseLoneGroup.java](TestComplexPhraseLoneGroup.java)
- [test-lines.diff](test-lines.diff)
- [TestLoneGroupCompatProbe.java](TestLoneGroupCompatProbe.java)
- [TestLoneGroupEdgeProbe.java](TestLoneGroupEdgeProbe.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
