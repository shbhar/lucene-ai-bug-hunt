# ComplexPhraseQueryParser skips its same-field check for prefix and regular-expression clauses: "jo* id:2*" parses and then fails at search time with IllegalArgumentException, and name:"id:2*" searches field id

Bug LFV-715 · Area: queryparser / complexPhrase: ComplexPhraseQueryParser (pass-2 same-field check for clauses nested in a phrase) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse a quoted phrase such as "jo* id:2*" or "jo* id:/2/" with ComplexPhraseQueryParser on the default field name; its class javadoc says the second pass checks that all embedded clauses refer to the same field, and it rejects "jo* id:2", "jo* id:2?", "jo* id:2~1" and "jo* id:[1 TO 2]" at parse time, so you expect a parse error. Instead parse succeeds, because the check is wired into newTermQuery, getWildcardQuery, getRangeQuery and getFuzzyQuery but the class never overrides getPrefixQuery or getRegexpQuery, and the search then throws IllegalArgumentException 'Clauses must have same field.'. A one-clause phrase such as name:"id:2*" also parses, and because rewrite returns a lone multi-term query unchanged, it searches field id and returns the document whose id is 2.

The test shows that ComplexPhraseQueryParser skips its same-field check for a prefix clause nested in a phrase, so parse("\"jo* id:2*\"") on default field name is accepted.

## Expected vs actual

Expected: ParseException ("Cannot have clause for field \"id\" nested in phrase for field \"name\""), as for "jo* id:2?". Actual (-ea and -da): parse() succeeds.

## Files

- [src/org/apache/lucene/queryparser/complexPhrase/TestComplexPhrasePrefixClauseOtherField.java](src/org/apache/lucene/queryparser/complexPhrase/TestComplexPhrasePrefixClauseOtherField.java) (JUnit test)

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
     execute --select-class org.apache.lucene.queryparser.complexPhrase.TestComplexPhrasePrefixClauseOtherField
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
