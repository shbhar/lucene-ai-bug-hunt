# Latent: the doc-values cursor that BlockJoinSelector.wrap returns can pass over a child's value when advance(target &gt; 0) is the first call on it, so the parent can get a wrong minimum or maximum (run: one block [child 0: 1, child 1: 5, parent 2], MIN: advance(1) stops at parent 2 with the value 5, nextDoc() with 1); block-join sorting reads the cursor with advanceExact() and was not affected in what was run

Finding LFV-1555 · Area: join / block-join value selection (BlockJoinSelector.wrap and its cursor ToParentDocValues) · Priority: low · Impact: minor · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap a child field's doc values with BlockJoinSelector.wrap(values, MIN, parents, children) for one block [child 0: price 1, child 1: price 5, parent 2] and make advance(1) the first call on the returned NumericDocValues; you expect it to stop at parent 2 with longValue() 1, which is what nextDoc(), advance(0) and advanceExact(2) give, each on a fresh cursor over the same block. Instead it stops at parent 2 with the value 5, silently, with assertions on and off: advance() has already put the child iterator on child 0, and the nextDoc() it then calls steps the child iterator once more because the parent cursor is still at -1, so child 0 never reaches the selection. It is latent: BlockJoinSelector is a public class tagged @lucene.internal, and block-join sorting through ToParentBlockJoinSortField, its one caller in Lucene's main sources, reads the cursor with advanceExact() (0 advance() and 0 nextDoc() calls on the cursor in 206,928 sorted searches on branch_11x f1b0f8e7c687 with assertions off), so sorting was not affected in what was run; it is not new in 11.0 (the same call fails the same way on the 10.6.0 and the 7.0.0 release jars).

## Expected vs actual

- Expected: You expect it to stop at parent 2 with longValue() 1, which is what nextDoc(), advance(0) and advanceExact(2) give, each on a fresh cursor over the same block.
- Actual: It stops at parent 2 with the value 5, silently, with assertions on and off: advance() has already put the child iterator on child 0, and the nextDoc() it then calls steps the child iterator once more because the parent cursor is still at -1, so child 0 never reaches the selection.

## Files

- [src/org/apache/lucene/search/join/TestBlockJoinSelectorFirstAdvance.java](src/org/apache/lucene/search/join/TestBlockJoinSelectorFirstAdvance.java) (JUnit test)

## Run it

- Tested on Lucene main 7b8418faa2ff (a commit of 2026-11-20): still reproduces.
- A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/join/build/libs/lucene-join-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed C0FFEE884
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=C0FFEE884 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestBlockJoinSelectorFirstAdvance
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
