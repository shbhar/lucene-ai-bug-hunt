# PriorityQueue.downHeap computes the child index i &lt;&lt; 1 in int, so a queue holding 2^30 or more elements throws ArrayIndexOutOfBoundsException from updateTop, pop or insertWithOverflow and is left with one element lost and another duplicated

Bug LFV-294 · Area: util / PriorityQueue (sift-down; base class of HitQueue and FieldValueHitQueue) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You fill a Lucene PriorityQueue with 2^30 + 1 elements, a size its constructor accepts (any maxSize below ArrayUtil.MAX_ARRAY_LENGTH), and call updateTop, pop or insertWithOverflow; you expect the usual log(size) sift-down and the new least element back. Instead, when the sift-down reaches node 2^30, the child index i &lt;&lt; 1 wraps to Integer.MIN_VALUE, the negative index -2147483647 passes the 'k &lt;= size' check, and the call throws ArrayIndexOutOfBoundsException after children were already moved up, so the queue has lost the element being sifted and holds a second copy of another. A queue of 2^30 - 1 elements works; exactly 2^30 already fails for updateTop.

## Expected vs actual

- Expected: You expect the usual log(size) sift-down and the new least element back.
- Actual: When the sift-down reaches node 2^30, the child index i &lt;&lt; 1 wraps to Integer.MIN_VALUE, the negative index -2147483647 passes the 'k &lt;= size' check, and the call throws ArrayIndexOutOfBoundsException after children were already moved up, so the queue has lost the element being sifted and holds a second copy of another.

## Files

- [src/org/apache/lucene/util/TestPriorityQueueDownHeapIndexOverflow.java](src/org/apache/lucene/util/TestPriorityQueueDownHeapIndexOverflow.java) (JUnit test)
- [src2/org/apache/lucene/util/TestPriorityQueueDownHeapIndexOverflow.java](src2/org/apache/lucene/util/TestPriorityQueueDownHeapIndexOverflow.java) (JUnit test)

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

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx7g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ParallelGCThreads=2 -XX:ConcGCThreads=1 -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestPriorityQueueDownHeapIndexOverflow
java -ea -esa -Xmx7g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ParallelGCThreads=2 -XX:ConcGCThreads=1 -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestPriorityQueueDownHeapIndexOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/priorityqueue-topdocs-merge/](../../models/priorityqueue-topdocs-merge/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
