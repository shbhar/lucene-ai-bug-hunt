# TimSorter's merge gallop (lowerSaved3) doubles its probe index in int, so CollectionUtil.timSort of a legal list of more than 2^30 elements (a short run followed by a sorted run of more than 2^30 smaller elements) reads a negative slot and throws IndexOutOfBoundsException in the middle of a merge; every release since 4.4.0

Finding LFV-1460 · Area: util / sorters (TimSorter merge galloping: lowerSaved3, reached from CollectionUtil.timSort, ArrayUtil.timSort and index sorting) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1460.html](../../bugs/LFV-1460.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You sort a legal RandomAccess list of 1,073,741,896 Booleans (64 true values, then 2^30 + 8 false values) with CollectionUtil.timSort in natural order and expect it back sorted. Instead TimSorter's merge gallop doubles its probe index in int: after 2^30 slots the index wraps to -2147483577, which still passes the 't &lt; to' check, so the sort asks the list for slot -2147483577 and throws IndexOutOfBoundsException. Because the merge had already moved the 64 true values into its temp buffer, the aborted list holds only 56 of them.

## Expected vs actual

- Expected: The list comes back sorted (1,073,741,832 `false`, then 64 `true`), so the first `true` is at slot 1,073,741,832.
- Actual: `IndexOutOfBoundsException: Index -2147483577 out of bounds for length 1073741896` from `TimSorter.lowerSaved3(TimSorter.java:339)`, with assertions on and off.

## Files

- [src/org/apache/lucene/util/TestTimSortGallopOverflow.java](src/org/apache/lucene/util/TestTimSortGallopOverflow.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestTimSortGallopOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestTimSortGallopOverflow.java](TestTimSortGallopOverflow.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
