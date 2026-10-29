# ImpactsSource#advanceShallow javadoc does not say that the current document's freq, positions and docIDRunEnd become invalid after a shallow advance past its block: the default codec then returns another block's data, and a lazy freq breaks the next legal advance (javadoc and assertion gap; the codec behaviour is intended)

Bug LFV-156 · Area: index / ImpactsEnum shallow-advance contract (ImpactsSource javadoc, test-framework AssertingImpactsEnum); codecs / Lucene104 postings reader · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You take an ImpactsEnum from TermsEnum#impacts, stand on a document, call advanceShallow(target) with a target in a later 256-doc block to peek at upcoming impacts, and then read the current document's freq, positions or docIDRunEnd; the javadoc forbids only nextDoc and too-small advance targets afterwards and says docID does not change, so you expect the current document's own data and a working advance(target). Instead the default codec has already moved its streams to the target block on purpose: on main the positions of doc 0 read as [5] instead of [0], docIDRunEnd claims docs 0 to 3327 all match although only every third does, and after freq the legal advance(3000) returns doc 3005, which does not contain the term. Lucene's own callers always advance before reading, so the codec behaviour is intended; what is missing is this precondition in the ImpactsSource javadoc and a check for it in the test framework's AssertingImpactsEnum.

The test shows that on a default-codec ImpactsEnum, the sequence nextDoc() (doc 0), advanceShallow(256), freq(), advance(256) returns the wrong doc. The `ImpactsSource#advanceShallow` javadoc allows this sequence; the javadoc gap is that it does not say per-doc reads are invalid after a shallow advance past the current block.

## Expected vs actual

Expected: 256 (every one of the 1200 docs contains t). Actual on main with -ea and with -da: 261, `expected:<256> but was:<261>`. Without the freq() call, advance returns 256.

## Files

- [src/org/apache/lucene/index/TestAdvanceShallowThenFreqThenAdvance.java](src/org/apache/lucene/index/TestAdvanceShallowThenFreqThenAdvance.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestAdvanceShallowThenFreqThenAdvance
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
