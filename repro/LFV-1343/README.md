# ComplexPhraseQueryParser: a phrase query's equals() ignores the phrase as analyzed by its parser, so the same phrase text parsed with two different analyzers gives two equal queries that match different documents, and a DisjunctionMaxQuery of the two silently evaluates only the first one (since 9.0.0)

Finding LFV-1343 · Area: queryparser / complexPhrase: ComplexPhraseQuery equality (consumed by core DisjunctionMaxQuery) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1343.html](../../bugs/LFV-1343.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse the phrase "Foo Bar" for field body twice with ComplexPhraseQueryParser, once with an analyzer that keeps case and once with StandardAnalyzer, and put both queries into a DisjunctionMaxQuery, which is documented to match the union of its subqueries; the folded query matches the indexed 'foo bar', so the dis_max should match it too. Instead it returns 0 hits: ComplexPhraseQuery.equals and hashCode compare only the field, the raw phrase text, slop and inOrder, not the phrase as parsed by each parser's analyzer, so the two queries compare equal although they match different documents, and DisjunctionMaxQuery, which since 9.0.0 keeps its disjuncts in a hash-based multiset, keeps only the first one and evaluates it twice. With the two disjuncts swapped the same dis_max returns 1 hit.

## Expected vs actual

- Expected: The dis_max finds 1 document, the one `folded` matches.
- Actual: `AssertionError: expected:<1> but was:<0>` at the dis_max assert: only `exact` ran.

## Files

- [src/org/apache/lucene/queryparser/complexPhrase/TestComplexPhraseQueryEqualsIgnoresAnalysis.java](src/org/apache/lucene/queryparser/complexPhrase/TestComplexPhraseQueryEqualsIgnoresAnalysis.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
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
     execute --select-class org.apache.lucene.queryparser.complexPhrase.TestComplexPhraseQueryEqualsIgnoresAnalysis
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestComplexPhraseQueryEqualsIgnoresAnalysis.java](TestComplexPhraseQueryEqualsIgnoresAnalysis.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
