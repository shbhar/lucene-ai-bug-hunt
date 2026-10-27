# ImpactsSource#advanceShallow javadoc does not say that the current document's freq, positions and docIDRunEnd become invalid after a shallow advance past its block: the default codec then returns another block's data, and a lazy freq breaks the next legal advance (javadoc and assertion gap; the codec behaviour is intended)

Bug LFV-156 · Area: index / ImpactsEnum shallow-advance contract (ImpactsSource javadoc, test-framework AssertingImpactsEnum); codecs / Lucene104 postings reader · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You take an ImpactsEnum from TermsEnum#impacts, stand on a document, call advanceShallow(target) with a target in a later 256-doc block to peek at upcoming impacts, and then read the current document's freq, positions or docIDRunEnd; the javadoc forbids only nextDoc and too-small advance targets afterwards and says docID does not change, so you expect the current document's own data and a working advance(target). Instead the default codec has already moved its streams to the target block on purpose: on main the positions of doc 0 read as [5] instead of [0], docIDRunEnd claims docs 0 to 3327 all match although only every third does, and after freq the legal advance(3000) returns doc 3005, which does not contain the term. Lucene's own callers always advance before reading, so the codec behaviour is intended; what is missing is this precondition in the ImpactsSource javadoc and a check for it in the test framework's AssertingImpactsEnum.

## Expected vs actual

- Expected: You expect the current document's own data and a working advance(target).
- Actual: The default codec has already moved its streams to the target block on purpose: on main the positions of doc 0 read as [5] instead of [0], docIDRunEnd claims docs 0 to 3327 all match although only every third does, and after freq the legal advance(3000) returns doc 3005, which does not contain the term.

## Files

- [src/org/apache/lucene/index/TestAdvanceShallowCurrentDoc.java](src/org/apache/lucene/index/TestAdvanceShallowCurrentDoc.java) (JUnit test)
- [src/org/apache/lucene/index/TestAdvanceShallowDocIDRunEnd.java](src/org/apache/lucene/index/TestAdvanceShallowDocIDRunEnd.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.index.TestAdvanceShallowCurrentDoc \
       --select-class org.apache.lucene.index.TestAdvanceShallowDocIDRunEnd
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
