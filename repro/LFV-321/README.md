# TermInSetQuery.equals/hashCode ignore the rewrite method, so set queries that match different documents compare equal, and IndexWriter's delete-by-query buffer then applies only one of them

Bug LFV-321 · Area: core / search: TermInSetQuery equality (MultiTermQuery rewrite methods); consequence in index / IndexWriter delete-by-query buffering · Priority: low · Impact: data corruption · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

Since 9.6.0 TermInSetQuery is a MultiTermQuery whose rewrite method decides which documents it matches and how it scores, but its equals/hashCode still compare only the field and terms: new TermInSetQuery("f", [a]) equals SortedSetDocValuesField.newSlowSetQuery("f", [a]) although on a field with only doc values they match 0 and 1 documents, and on an ordinary field new TermInSetQuery("f", [a, b]) equals its TopTermsScoringBooleanQueryRewrite(1) variant although they match 2 and 1 documents. You expect queries that match different documents to be unequal, as PrefixQuery, AutomatonQuery, FuzzyQuery and TermRangeQuery are when only the rewrite method differs. Instead anything keyed on the un-rewritten query treats the two as one: IndexWriter.deleteDocuments with both queries in one flush window applies only the first, so a doc-values delete silently does not happen, or a document added in between is deleted although no call matched it.

## Expected vs actual

- Expected: You expect queries that match different documents to be unequal, as PrefixQuery, AutomatonQuery, FuzzyQuery and TermRangeQuery are when only the rewrite method differs.
- Actual: Anything keyed on the un-rewritten query treats the two as one: IndexWriter.deleteDocuments with both queries in one flush window applies only the first, so a doc-values delete silently does not happen, or a document added in between is deleted although no call matched it.

## Files

- [src/org/apache/lucene/search/TestTermInSetQueryEqualsIgnoresRewriteMethod.java](src/org/apache/lucene/search/TestTermInSetQueryEqualsIgnoresRewriteMethod.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestTermInSetQueryEqualsIgnoresRewriteMethod
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
