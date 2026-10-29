# SpanMultiTermQueryWrapper.equals/hashCode ignore the span rewrite method (since 5.5.0), so wrappers that match different documents compare equal and IndexWriter's delete-by-query buffer keeps only one of two such deletes

Bug LFV-946 · Area: queries / spans: SpanMultiTermQueryWrapper equality (span rewrite methods); consequence in index / IndexWriter delete-by-query buffering · Priority: unranked · Impact: data corruption · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build new SpanMultiTermQueryWrapper(new PrefixQuery(f:aa)) twice and call setRewriteMethod(new TopTermsSpanBooleanQueryRewrite(1)) on one of them, so on a 6-document index one wrapper matches all 6 documents and the other matches 1, and you expect the two to be unequal, as two PrefixQuery objects that differ only in their rewrite method are. Instead equals and hashCode compare only the wrapped PrefixQuery, so the wrappers are equal with equal hash codes. IndexWriter buffers delete-by-query calls in a HashMap keyed by the query, so deleteDocuments(top1) followed by deleteDocuments(all) in one flush window keeps only the first and commits with 5 of the 6 matching documents still live, and in the other order a document added between the two calls is deleted although no delete issued after it matches it.

## Expected vs actual

- Expected: You expect the two to be unequal, as two PrefixQuery objects that differ only in their rewrite method are.
- Actual: Equals and hashCode compare only the wrapped PrefixQuery, so the wrappers are equal with equal hash codes.

## Files

- [src/org/apache/lucene/queries/spans/TestSpanMultiTermQueryWrapperEqualsRewriteMethod.java](src/org/apache/lucene/queries/spans/TestSpanMultiTermQueryWrapperEqualsRewriteMethod.java) (JUnit test)
- [src/org/apache/lucene/queries/spans/TestSpanFrequentTermsWrapperDeleteAliasing.java](src/org/apache/lucene/queries/spans/TestSpanFrequentTermsWrapperDeleteAliasing.java) (JUnit test)
- [src/SpanWrapperEqualsOldApi.java](src/SpanWrapperEqualsOldApi.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.spans.TestSpanMultiTermQueryWrapperEqualsRewriteMethod

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.spans.TestSpanFrequentTermsWrapperDeleteAliasing
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" SpanWrapperEqualsOldApi
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
