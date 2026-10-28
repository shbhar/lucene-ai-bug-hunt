# Stempel's Trie.reduce with Gener or Lift rewrites the cells of the trie it is given, so reducing PolishAnalyzer.getDefaultTable, documented as unmodifiable, silently changes the stems of every later PolishAnalyzer and StempelPolishStemFilterFactory in the JVM

Bug LFV-696 · Area: analysis/stempel / egothor stemmer trie reductions (Trie.reduce with Lift and Gener; the shared PolishAnalyzer.getDefaultTable table) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-696.html](../../bugs/LFV-696.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call reduce(new Gener) or reduce(new Lift(..)) on a stempel Trie, for example on PolishAnalyzer.getDefaultTable, which is documented as 'an unmodifiable instance of the default stemmer table', and expect what Trie.reduce's javadoc says, 'the newly reduced Trie', with the input left as it was (Optimizer and Optimizer2 behave so). Instead Lift.optimize and Gener.optimize take the input's own row list and rewrite its cells in place (Lift moves commands up a level and clears references, Gener drops infrequent commands and references) before copying them into the result, so the input answers differently afterwards: after PolishAnalyzer.getDefaultTable.reduce(new Gener) every new PolishAnalyzer stems 'książkami rzekami pracowali' to [książk, rzekami, pracowali] instead of [książek, rzekść, pracować], and a one-key trie {aa -&gt; Da} reduced with Lift(false) starts to answer getLastOnPath('a') = Da instead of nothing. Copying the rows before the rewrite fixes it; upstream TestStemmer, which reduces one trie five times, then needs a fresh trie per reduction.

## Expected vs actual

- Expected: A new `PolishAnalyzer` still gives [książek, rzekść, pracować], and the input trie {aa -&gt; Da} still answers nothing for 'a'.
- Actual: It gives [książk, rzekami, pracowali], and the input answers `Da` for 'a': 2 of 2 tests fail, with assertions on and off.

## Files

- [src/org/apache/lucene/analysis/stempel/TestReduceKeepsInput.java](src/org/apache/lucene/analysis/stempel/TestReduceKeepsInput.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/stempel/build/libs/lucene-analysis-stempel-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.stempel.TestReduceKeepsInput
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestReduceKeepsInput.java](TestReduceKeepsInput.java)
- [TestReduceLeavesInputUnchanged.java](TestReduceLeavesInputUnchanged.java)
- [ReduceKeepsInputHistoryMain.java](ReduceKeepsInputHistoryMain.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
