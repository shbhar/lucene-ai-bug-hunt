# Sandbox MultiRangeQuery point builders (IntPoint, LongPoint, FloatPoint, DoublePointMultiRangeBuilder) and PhraseWildcardQuery.Builder hand the built query the builder's own list, so adding to the builder after build() silently changes the earlier query's hits, equals/hashCode and toString (since 8.3.0)

Finding LFV-1469 · Area: sandbox / search: MultiRangeQuery point builders and PhraseWildcardQuery.Builder (build() hands over the builder's own list) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1469.html](../../bugs/LFV-1469.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You create one IntPointMultiRangeBuilder, add the range [0,0], build q1, then add [3,3] and build again. You expect q1 to keep matching only pt=0, since build() is documented to 'Create a new MultiRangeQuery based on the parameters that have been set on this builder' and the core query builders (BooleanQuery, PhraseQuery, MultiPhraseQuery) copy their state on build(). Instead build() gives q1 the builder's own clause list, so the later add() changes q1 too: it prints pt:{[0 TO 0]},{[3 TO 3]}, counts 2 instead of 1 and its hashCode changes; PhraseWildcardQuery.Builder does the same with its term list although that class is documented 'Immutable.' and safe as a query-cache key.

## Expected vs actual

- Expected: Each query keeps its build-time clauses: 0 hits for `pt:{[0 TO 0]}` on `pt=3`, 1 hit for `"x b*"` on `x bb`.
- Actual: `pt:{[0 TO 0]},{[3 TO 3]} expected:<0> but was:<1>` and `phraseWildcard(body:"x b* z") expected:<1> but was:<0>`, assertions on and off.

## Files

- [src/org/apache/lucene/sandbox/search/TestBuilderReuseAfterBuild.java](src/org/apache/lucene/sandbox/search/TestBuilderReuseAfterBuild.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
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
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.sandbox.search.TestBuilderReuseAfterBuild
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestBuilderReuseAfterBuild.java](TestBuilderReuseAfterBuild.java)

Formal models and scripts this finding's part of the hunt used: [../../models/sandbox-multirange-point-builders-and-phrasewildcardquery-builder-hand-their-own-list-to-built-queries-builder-reuse-rewrites-earlier-query-and-its-cache-key-since-8-3-0-verify-low/](../../models/sandbox-multirange-point-builders-and-phrasewildcardquery-builder-hand-their-own-list-to-built-queries-builder-reuse-rewrites-earlier-query-and-its-cache-key-since-8-3-0-verify-low/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
