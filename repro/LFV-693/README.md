# Stempel: running Optimizer or Optimizer2 on an already merge-reduced trie silently corrupts it, because Optimizer.optimize remaps references to rows it has not placed yet to row 0

Bug LFV-693 · Area: analysis-stempel / egothor stemmer trie reductions (Optimizer, Optimizer2; Compile tool, Trie.reduce) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-693.html](../../bugs/LFV-693.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a stempel table with the Compile tool and a method string that has two merge passes (for example '-M22' or '-0ME22'), or you call reduce(new Optimizer2) (or Optimizer) on a table that a merge pass already reduced, such as a loaded table; Optimizer2's javadoc says its row joining keeps the stemmer able to 'recognize words for which the original trie was built', so the training words should keep their stems, as they do after one pass. Instead the second pass silently corrupts the trie: two Optimizer2 passes over the one-word trie {aab -&gt; Da} lose its only entry, Compile '-22' stems every training form of a small table to null and '-M22' leaves every form unstemmed (while '-2' and '-M2' are correct), and one more Optimizer2 pass over the bundled Polish table changes 8,356 of 21,147 keys whose whole path is stored. The cause is Optimizer.optimize (inherited by Optimizer2): it walks the rows from the highest index down and remaps every child reference through a remap array that is still 0 for rows it has not placed yet, which is only correct when every child has a higher index than its parent; a freshly built trie has that property, but the output of a merge pass does not (shared rows, self-loops, pre-order renumbering).

## Expected vs actual

- Expected: `Da` after two `Optimizer2` passes, `Db` after two `Optimizer` passes, and all 9 training words stemmed with `-22` and `-M22`, as with `-2` and `-M2`.
- Actual: `null` after the second `Optimizer2` pass, `Da` for `ab` after the second `Optimizer` pass; `-22` stems all 9 words to `null`, `-M22` returns all 9 unchanged. 3 of 3 tests fail, assertions on and off.

## Files

- [src/org/apache/lucene/analysis/stempel/TestOptimizerSecondPass.java](src/org/apache/lucene/analysis/stempel/TestOptimizerSecondPass.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.stempel.TestOptimizerSecondPass
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestSecondMergePass.java](TestSecondMergePass.java)
- [TestRepeatedOptimizer2.java](TestRepeatedOptimizer2.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
