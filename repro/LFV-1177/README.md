# ConjunctionUtils.createConjunction sorts the caller's list of TwoPhaseIterators in place, so an immutable list such as List.of(a, b) whose match costs are not in ascending order throws UnsupportedOperationException, and a LinkedList throws IllegalArgumentException (since 9.0.0)

Finding LFV-1177 · Area: core / search: conjunction iterators (ConjunctionUtils.createConjunction, ConjunctionDISI.ConjunctionTwoPhaseIterator) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1177.html](../../bugs/LFV-1177.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You combine two TwoPhaseIterators with the public ConjunctionUtils.createConjunction(List.of(a.approximation(), b.approximation()), List.of(a, b)), where a has matchCost 100 and b has matchCost 1. You expect a conjunction iterator over their common documents, which is what you get when the same list happens to be in ascending cost order: the javadoc describes both arguments only as lists 'to combine', and the method copies its first list before sorting it. Instead it throws UnsupportedOperationException, because the private ConjunctionTwoPhaseIterator constructor sorts the caller's second list in place by matchCost (CollectionUtil.timSort) before copying it; a LinkedList makes the same call throw IllegalArgumentException 'CollectionUtil can only sort random access lists in-place.' in any order, and a mutable ArrayList comes back reordered.

## Expected vs actual

- Expected: `createConjunction` returns a conjunction whose `nextDoc()` is 0.
- Actual: `java.lang.UnsupportedOperationException` from `ImmutableCollections` at `ConjunctionDISI$ConjunctionTwoPhaseIterator.<init>(ConjunctionDISI.java:354)`.

## Files

- [src/org/apache/lucene/search/TestConjunctionTwoPhaseListOf.java](src/org/apache/lucene/search/TestConjunctionTwoPhaseListOf.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestConjunctionTwoPhaseListOf
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestConjunctionTwoPhaseListOf.java](TestConjunctionTwoPhaseListOf.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
