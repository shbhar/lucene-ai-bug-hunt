# SpanMultiTermQueryWrapper.equals/hashCode ignore the span rewrite method (since 5.5.0), so wrappers that match different documents compare equal and IndexWriter's delete-by-query buffer keeps only one of two such deletes

Finding LFV-946 · Area: queries / spans: SpanMultiTermQueryWrapper equality (span rewrite methods); consequence in index / IndexWriter delete-by-query buffering · Priority: low · Impact: data corruption · Novelty: known upstream

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

- Tested on Lucene main 11c8b6ea99ae (a commit of 2026-11-23): still reproduces.
- Tested on Lucene main a9779ef3c193 (a commit of 2026-10-26): reproduced.
- The javac line below compiles the three files under src/ together. src/SpanWrapperEqualsOldApi.java, the main() class of the last command, uses the span queries from org.apache.lucene.search.spans in lucene-core, their package in the releases up to 8.11.4 (from 9.0.0 on it is org.apache.lucene.queries.spans in lucene-queries); it does not compile on a build of Lucene main or of branch_11x or with the jars of the 10.6.0 release, so as printed the javac line stops there, the two JUnit commands find no test and the last command cannot load its class. Write find src/org in place of find src in the javac line and leave out the last java command. Then, run on 2026-11-22 on a build of main b5a4546a5964, on a build of branch_11x 262e019500ec (there V=11.0.0-SNAPSHOT) and with the 10.6.0 jars (Maven Central, org.apache.lucene, in place of the five $LUCENE jars; V=10.6.0), with assertions on and off: run 1 reports 4 tests found and 4 failed, and the JUnit command of run 2 reports 3 tests found and 3 failed. Its class, TestSpanFrequentTermsWrapperDeleteAliasing, uses FrequentTermsSpanBooleanQueryRewrite, which no release before 10.6.0 has: with the jars of 9.12.3 name only src/org/apache/lucene/queries/spans/TestSpanMultiTermQueryWrapperEqualsRewriteMethod.java in the javac line and run only run 1 (4 tests found and 4 failed, run the same day with assertions on). SpanWrapperEqualsOldApi.java, compiled alone and run with lucene-core and lucene-analyzers-common of 8.11.4 and of 5.5.0 on the class path, prints 'PROBE violations=2' and exits with status 1 (run the same day with assertions on).
- How to read the outcome: a failing test is the finding reproducing: with the javac line and the commands as the note above says, run 1 reports 4 tests found and 4 tests failed and the JUnit command of run 2 reports 3 tests found and 3 tests failed; the main() class SpanWrapperEqualsOldApi, compiled and run alone with the jars of 8.11.4 or of 5.5.0, ends its output with the line PROBE violations=2 and exits with status 1, without an exception.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
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

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
