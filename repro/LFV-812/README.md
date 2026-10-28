# IndexWriter.deleteAll javadoc says deleteDocuments(MatchAllDocsQuery) is slower and only marks documents deleted, but since 5.2.0 (LUCENE-6379) deleteDocuments(Query...) runs an exact MatchAllDocsQuery as deleteAll, which also resets the field schema (javadoc only; the routing is intended)

Bug LFV-812 · Area: index / IndexWriter delete APIs (deleteAll and deleteDocuments(Query...) javadoc) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a NUMERIC doc values field "f", commit, call deleteDocuments(MatchAllDocsQuery.INSTANCE) and then add "f" as SORTED. You expect the 'cannot change doc values type' IllegalArgumentException, because the deleteAll javadoc calls deleteAll 'much faster than' that call and says a delete query 'only marks documents as deleted' while deleteAll clears doc values types. Instead the SORTED document is accepted and getFieldNames is empty: since LUCENE-6379 (5.2.0), deleteDocuments(Query...) returns deleteAll whenever a query's class is exactly MatchAllDocsQuery, while the same delete wrapped in a ConstantScoreQuery keeps the schema; the routing is deliberate, only the javadoc is wrong.

## Expected vs actual

- Expected: You expect the 'cannot change doc values type' IllegalArgumentException, because the deleteAll javadoc calls deleteAll 'much faster than' that call and says a delete query 'only marks documents as deleted' while deleteAll clears doc values types.
- Actual: The SORTED document is accepted and getFieldNames is empty: since LUCENE-6379 (5.2.0), deleteDocuments(Query...) returns deleteAll whenever a query's class is exactly MatchAllDocsQuery, while the same delete wrapped in a ConstantScoreQuery keeps the schema; the routing is deliberate, only the javadoc is wrong.

## Files

- [src/org/apache/lucene/index/TestDeleteByMatchAllSemantics.java](src/org/apache/lucene/index/TestDeleteByMatchAllSemantics.java) (JUnit test)
- [src2/org/apache/lucene/index/TestDeleteByMatchAllSemantics.java](src2/org/apache/lucene/index/TestDeleteByMatchAllSemantics.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions off (production-like), seed 2990C0DE
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=2990C0DE \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestDeleteByMatchAllSemantics
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=2990C0DE \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestDeleteByMatchAllSemantics
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
