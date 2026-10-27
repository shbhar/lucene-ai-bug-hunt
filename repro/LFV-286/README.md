# Intervals.atLeast stops early when the sub-iterator it just used runs out, so it loses minimal intervals and misses documents when nested; the same hand-over also lets a stale sub-iterator re-enter and return a non-minimal interval out of order

Bug LFV-286 · Area: queries / intervals (Intervals.atLeast: MinimumShouldMatchIntervalsSource) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-286.html](../../bugs/LFV-286.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 'a b' and read the intervals of Intervals.atLeast(1, term a, term b), or search ordered(c, atLeast(1, a, b)) over 'a c b'; you expect [0,0] and [1,1], exactly what or(a, b) returns, and one hit. Instead atLeast returns only [0,0] and the ordered query finds nothing: when the sub-iterator just popped from atLeast's proximity queue has no further interval, nextInterval does not pull a replacement from its background queue and ends, although the other source still has intervals. The same hand-over never advances waiting sub-iterators past the current start, so atLeast(1, b, phrase(a, b)) on 'a b b' returns [1,1] then [0,1] (out of order, and [0,1] contains [1,1]) and loses [2,2].

## Expected vs actual

- Expected: `[0,0], [1,1]` on "a b"; 1 hit for the nested query on "a c b"; `[0,2], [1,3]` for k = 2 on "a b c d"; `[1,1], [2,2]` on "a b b".
- Actual: `[0,0]`; 0 hits; `[0,2]`; `[1,1], [0,1]` (out of order, not minimal): every case loses intervals.

## Files

- [src/org/apache/lucene/queries/intervals/TestAtLeastExhaustedSource.java](src/org/apache/lucene/queries/intervals/TestAtLeastExhaustedSource.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.intervals.TestAtLeastExhaustedSource
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestAtLeastLostIntervals.java](TestAtLeastLostIntervals.java)
- [hand-over.diff](hand-over.diff)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
