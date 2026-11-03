# Calling docIDRunEnd() on a two-phase disjunction's approximation between matches() and score() corrupts DisjunctionScorer's list of verified matches, so score() and getChildren() include a clause that does not match the current document, or never return (since 10.2.0)

Finding LFV-1143 · Area: core / search (DisjunctionScorer two-phase verified matches vs DisjunctionDISIApproximation.docIDRunEnd) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1143.html](../../bugs/LFV-1143.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index two documents {0: "b a c", 1: "c"} and build BooleanQuery(SHOULD f:c, SHOULD phrase "a b") with ScoreMode.COMPLETE; you move a scorer to doc 0 with iterator().nextDoc() and ask twoPhaseIterator().approximation().docIDRunEnd() (it returns 1 and the scorer stays on doc 0). You expect score() to return 0.06880058, the f:c score a fresh scorer gives, because docIDRunEnd() is documented not to affect the iterator; you get 0.59193045 with no error, because the phrase, which does not match doc 0, is added in. docIDRunEnd() walks the lead heap's topList(), which rewrites the same DisiWrapper.next links that matches() used to build the list of verified matches, and if the phrase also matches doc 0 the list becomes a cycle and score() never returns.

## Expected vs actual

- Expected: `score()` on doc 0 gives 0.06880058, the same as a fresh scorer (only `f:c` matches doc 0).
- Actual: `expected:<0.06880058> but was:<0.59193045>`, with assertions on and off.

## Files

- [src/org/apache/lucene/search/TestDisjunctionApproximationDocIdRunEnd.java](src/org/apache/lucene/search/TestDisjunctionApproximationDocIdRunEnd.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestDisjunctionApproximationDocIdRunEnd
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestDisjunctionApproximationDocIdRunEnd.java](TestDisjunctionApproximationDocIdRunEnd.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
