# IndexWriter.deleteAll() javadoc says deleteDocuments(MatchAllDocsQuery) is slower and only marks documents deleted, but since 5.2.0 (LUCENE-6379) deleteDocuments(Query...) runs an exact MatchAllDocsQuery as deleteAll(), which also resets the field schema (javadoc only; the routing is intended)

Finding LFV-812 · Area: index / IndexWriter delete APIs (deleteAll and deleteDocuments(Query...) javadoc) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a NUMERIC doc values field "f", commit, call deleteDocuments(MatchAllDocsQuery.INSTANCE) and then add "f" as SORTED. You expect the 'cannot change doc values type' IllegalArgumentException, because the deleteAll() javadoc calls deleteAll() 'much faster than' that call and says a delete query 'only marks documents as deleted' while deleteAll() clears doc values types. Instead the SORTED document is accepted and getFieldNames() is empty: since LUCENE-6379 (5.2.0), deleteDocuments(Query...) returns deleteAll() whenever a query's class is exactly MatchAllDocsQuery, while the same delete wrapped in a ConstantScoreQuery keeps the schema; the routing is deliberate, only the javadoc is wrong.

## Expected vs actual

- Expected: You expect the 'cannot change doc values type' IllegalArgumentException, because the deleteAll() javadoc calls deleteAll() 'much faster than' that call and says a delete query 'only marks documents as deleted' while deleteAll() clears doc values types.
- Actual: The SORTED document is accepted and getFieldNames() is empty: since LUCENE-6379 (5.2.0), deleteDocuments(Query...) returns deleteAll() whenever a query's class is exactly MatchAllDocsQuery, while the same delete wrapped in a ConstantScoreQuery keeps the schema; the routing is deliberate, only the javadoc is wrong.

## Files

- [src/org/apache/lucene/index/TestDeleteByMatchAllSemantics.java](src/org/apache/lucene/index/TestDeleteByMatchAllSemantics.java) (JUnit test)
- [src2/org/apache/lucene/index/TestDeleteByMatchAllSemantics.java](src2/org/apache/lucene/index/TestDeleteByMatchAllSemantics.java) (JUnit test)

## Run it

- Tested on Lucene main 11c8b6ea99ae (a commit of 2026-11-23): still reproduces.
- Tested on Lucene main a9779ef3c193 (a commit of 2026-10-26): reproduced.
- This folder holds two copies of the test: the copy under src/ passes MatchAllDocsQuery.INSTANCE and the copy under src2/ calls new MatchAllDocsQuery() (lines 62 and 67), the form that also compiles with the jars of 9.1.0, 9.12.3, 10.0.0 and 10.3.2, which do not have the constant (it exists from Lucene 10.4.0 on). On Lucene main (since 24d9e7d1a0d) and on the 11.0 branch (branch_11x, since a6a26f9b251) that constructor is private (#16962, merged 2026-11-03), so on a build of either the second javac line stops at those two lines and the second java command finds no test: leave these two commands out there. The first javac line and the first java command are the repro on such a build. With the jars of the 10.6.0 release (Maven Central, org.apache.lucene, in place of the three $LUCENE jars; V=10.6.0) both copies compile and both commands give the same outcome. Run on 2026-11-22 with assertions on and off: each command reports 2 tests found and 1 failed, the test of the MatchAllDocsQuery delete, with 'Expected exception IllegalArgumentException but no exception was thrown'; the control with the wrapped query passes. The src/ copy was run on a build of main b5a4546a5964, on a build of the 11.0 branch (branch_11x 262e019500ec; there V=11.0.0-SNAPSHOT) and with the 10.6.0 jars, the src2/ copy with the 10.6.0 jars.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
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

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
