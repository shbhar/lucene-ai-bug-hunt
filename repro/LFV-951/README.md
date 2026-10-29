# CompositeVerifyQuery.visit reports the whole query as a leaf and never visits the index query it rewrites and executes, so QueryVisitor term collection misses the index query's terms and IndexSearcher's cumulative maxClauseCount check does not count its clauses

Bug LFV-951 · Area: spatial-extras / composite: CompositeVerifyQuery (Query#visit; consumers: QueryVisitor.termCollector and other visit-based term extraction, IndexSearcher's cumulative clause limit) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap your own index query, for example TermQuery(body:a) or a 20-clause DisjunctionMaxQuery, in new CompositeVerifyQuery(indexQuery, shapePredicate), then collect its terms with QueryVisitor.termCollector or search it after IndexSearcher.setMaxClauseCount(8). You expect [body:a] and an IndexSearcher.TooManyNestedClauses error, because Query#visit is documented to 'recurse through the query tree, visiting any child queries', the wrapper rewrites its index query and runs it as the approximation that every hit must match, and the same 20 clauses as a bare query or as a sibling FILTER clause are rejected. Instead CompositeVerifyQuery.visit only calls visitor.visitLeaf(this): termCollector returns and the clause check counts the whole query as one clause, so the over-limit query runs (its hits are correct).

## Expected vs actual

- Expected: You expect [body:a] and an IndexSearcher.TooManyNestedClauses error, because Query#visit is documented to 'recurse through the query tree, visiting any child queries', the wrapper rewrites its index query and runs it as the approximation that every hit must match, and the same 20 clauses as a bare query or as a sibling FILTER clause are rejected.
- Actual: CompositeVerifyQuery.visit only calls visitor.visitLeaf(this): termCollector returns and the clause check counts the whole query as one clause, so the over-limit query runs (its hits are correct).

## Files

- [src/org/apache/lucene/spatial/composite/TestCompositeVerifyQueryVisit.java](src/org/apache/lucene/spatial/composite/TestCompositeVerifyQueryVisit.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/spatial-extras/build/libs/lucene-spatial-extras-$V.jar
CP=$CP:$LUCENE/lucene/spatial3d/build/libs/lucene-spatial3d-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial.composite.TestCompositeVerifyQueryVisit
```

The module(s) spatial-extras also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
