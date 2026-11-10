# ConjunctionUtils.createConjunction's javadoc does not say that every TwoPhaseIterator's approximation must also be in allIterators, so a caller who passes a DocIdSetIterator and a TwoPhaseIterator as the two lists 'to combine' silently gets wrong documents, extra or missing (javadoc only; the code works as designed; since 9.0.0)

Finding LFV-1442 · Area: core / search: conjunction iterators (ConjunctionUtils.createConjunction javadoc; ConjunctionDISI) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1442.html](../../bugs/LFV-1442.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call the public ConjunctionUtils.createConjunction(allIterators, twoPhaseIterators) with DocIdSetIterator.all(10) in the first list and, in the second, a TwoPhaseIterator whose approximation is DocIdSetIterator.range(4, 7) and whose matches() always returns true. The javadoc describes both arguments only as lists 'to combine', and a TwoPhaseIterator stands for the documents of its approximation that matches() confirms, so you expect [4, 5, 6], which is what intersectIterators with TwoPhaseIterator.asDocIdSetIterator, or lists built with addIterator/addTwoPhaseIterator, return. Instead you get all ten documents with no error: the factory leads the iteration with allIterators only and uses the two-phase list only as per-document checks, so an approximation that is not also in allIterators is never advanced and matches() runs while it is still at -1 (with a matches() that reads its approximation, true hits are dropped instead).

## Expected vs actual

- Expected: The conjunction returns `[4, 5, 6]`.
- Actual: It returns `[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]`, with no error, with assertions on and off.

## Files

- [src/org/apache/lucene/search/TestCreateConjunctionTwoPhaseApproximation.java](src/org/apache/lucene/search/TestCreateConjunctionTwoPhaseApproximation.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestCreateConjunctionTwoPhaseApproximation
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestCreateConjunctionTwoPhaseApproximation.java](TestCreateConjunctionTwoPhaseApproximation.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
