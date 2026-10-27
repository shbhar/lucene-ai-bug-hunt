# Flexible StandardQueryParser: the (...)@N min-should-match operator throws ClassCastException when the group does not build a BooleanQuery, e.g. (the fox)@1 with a stop-word analyzer or the boosted group (fox dog)^2@1

Bug LFV-732 · Area: queryparser / flexible: standard query builders (MinShouldMatchNodeBuilder for the @N min-should-match operator, composed with the grammar's group boost and the single-child Boolean optimization) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse (the fox)@1 ('at least one of the, fox') with the flexible StandardQueryParser and an analyzer that drops the stopword 'the', or the boosted group (fox dog)^2@1; the class javadoc documents @N for disjunction groups and boosts on grouped clauses, and parse declares QueryNodeException for failures, so you expect a query (at least one of the remaining clauses, boosted as written). Instead parse throws java.lang.ClassCastException ('TermQuery / BoostQuery cannot be cast to BooleanQuery') from MinShouldMatchNodeBuilder.build, which casts the group's query to BooleanQuery without a check: after the stopword is removed the one-clause group is collapsed to a TermQuery, and the grammar puts the boost inside the min-should-match node, so the group's query is a BoostQuery. Groups holding a single term, phrase, prefix or other non-Boolean query, such as (fox)@1 or ("quick fox")@1, fail the same way, while (fox dog)@1 works.

## Expected vs actual

- Expected: You expect a query (at least one of the remaining clauses, boosted as written).
- Actual: parse throws java.lang.ClassCastException ('TermQuery / BoostQuery cannot be cast to BooleanQuery') from MinShouldMatchNodeBuilder.build, which casts the group's query to BooleanQuery without a check: after the stopword is removed the one-clause group is collapsed to a TermQuery, and the grammar puts the boost inside the min-should-match node, so the group's query is a BoostQuery.

## Files

- [src/org/apache/lucene/queryparser/flexible/standard/TestMinShouldMatchNonBooleanGroup.java](src/org/apache/lucene/queryparser/flexible/standard/TestMinShouldMatchNonBooleanGroup.java) (JUnit test)

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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queryparser.flexible.standard.TestMinShouldMatchNonBooleanGroup
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
