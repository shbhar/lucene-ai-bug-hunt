# IndriAndQuery accepts MUST, FILTER and MUST_NOT clauses without complaint but ignores them when matching (it always matches the union of all clauses), while its explain does read MUST/FILTER, so it explains some of its own hits as non-matches

Bug LFV-871 · Area: search / IndriAndQuery (clause Occur handling in matching and explain) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an IndriAndQuery from BooleanClauses with Occur values other than SHOULD, for example [MUST f:a, FILTER acl:x] or [SHOULD f:a, MUST_NOT f:secret], which the public constructor accepts, and run it. You expect either the meaning that BooleanClause.Occur documents (the same clauses in a BooleanQuery give {0, 2} and {0, 1}) or a rejection, and in any case an explain that agrees with the hits. Instead IndriAndWeight builds one disjunction over all clauses whatever their Occur, so MUST and FILTER clauses do not restrict ({0, 1, 2, 3}), a MUST_NOT clause adds the very documents it names ({0, 1, 2}; a lone MUST_NOT clause returns exactly them), and IndriAndWeight.explain, which does treat MUST/FILTER clauses as required, explains such hits as non-matches.

## Expected vs actual

- Expected: You expect either the meaning that BooleanClause.Occur documents (the same clauses in a BooleanQuery give {0, 2} and {0, 1}) or a rejection, and in any case an explain that agrees with the hits.
- Actual: IndriAndWeight builds one disjunction over all clauses whatever their Occur, so MUST and FILTER clauses do not restrict ({0, 1, 2, 3}), a MUST_NOT clause adds the very documents it names ({0, 1, 2}; a lone MUST_NOT clause returns exactly them), and IndriAndWeight.explain, which does treat MUST/FILTER clauses as required, explains such hits as non-matches.

## Files

- [src/org/apache/lucene/search/TestIndriAndQueryClauseOccur.java](src/org/apache/lucene/search/TestIndriAndQueryClauseOccur.java) (JUnit test)

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

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestIndriAndQueryClauseOccur
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/indri-query-family-composite-score-boost-visitor-explain-laws/](../../models/indri-query-family-composite-score-boost-visitor-explain-laws/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
