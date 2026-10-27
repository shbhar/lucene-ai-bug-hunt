# Stempel's base Reduce.optimize passes its new, empty row list to removeGaps instead of the trie's rows, so every Trie.reduce(new Reduce) call throws IndexOutOfBoundsException

Bug LFV-692 · Area: analysis-stempel / egothor stemmer trie reduction (Reduce.optimize, reached through Trie.reduce, MultiTrie.reduce and MultiTrie2.reduce) · Priority: low · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You reduce a stempel stemmer table with the base class of the egothor reductions, for example trie.reduce(new Reduce), and expect what its javadoc promises: 'Optimize (remove holes in the rows) the given Trie and return the restructured Trie', a trie that still gives the same stems. Instead every call throws IndexOutOfBoundsException ('Index 0 out of bounds for length 0'), even for a one-word trie or the bundled Polish table. The cause is one wrong argument at Reduce.java:83: optimize passes its freshly created, empty list 'rows' to removeGaps as the list to read from, instead of 'orows' (the trie's own rows), which the sibling reductions Lift and Gener pass to the same helper.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Every call throws IndexOutOfBoundsException ('Index 0 out of bounds for length 0'), even for a one-word trie or the bundled Polish table.

## Files

- [src/org/apache/lucene/analysis/stempel/TestReduceBase.java](src/org/apache/lucene/analysis/stempel/TestReduceBase.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.stempel.TestReduceBase
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
