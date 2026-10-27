# Sorter.heapChild overflows int, so IntroSorter's heap-sort fallback compares a negative slot outside [from, to) when 2*to - from &gt; 2^31 (ArrayIndexOutOfBoundsException from ArrayUtil.introSort on a legal array slice)

Bug LFV-372 · Area: util / sorters (Sorter heap sort, IntroSorter's depth fallback) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You sort a legal slice with IntroSorter, for example ArrayUtil.introSort on the last 1,000 elements of an array of length ArrayUtil.MAX_ARRAY_LENGTH (or a slice of more than 2^30 elements starting at 0), and the element order makes IntroSorter give up on quicksort and fall back to heap sort. You expect the slice [from, to) to be sorted using only slots inside it. Instead Sorter.heapChild computes 2*(i - from) + 1 + from in int, which wraps to a negative number for a node that has no child, siftDown's 'leftChild &lt; to' check lets it through, and compare is called with a negative slot, so the array-backed sort throws ArrayIndexOutOfBoundsException ('Index -2147482757 out of bounds for length 2147483631').

## Expected vs actual

- Expected: You expect the slice [from, to) to be sorted using only slots inside it.
- Actual: Sorter.heapChild computes 2*(i - from) + 1 + from in int, which wraps to a negative number for a node that has no child, siftDown's 'leftChild &lt; to' check lets it through, and compare is called with a negative slot, so the array-backed sort throws ArrayIndexOutOfBoundsException ('Index -2147482757 out of bounds for length 2147483631').

## Files

- [src/org/apache/lucene/util/TestSorterHeapChildOverflow.java](src/org/apache/lucene/util/TestSorterHeapChildOverflow.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestSorterHeapChildOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/sorters-selectors/](../../models/sorters-selectors/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
