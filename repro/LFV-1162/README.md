# new CharArraySet(startSize, ignoreCase) and new CharArrayMap(startSize, ignoreCase) never return for a startSize from 858,993,460 to 1,717,986,918 or at most -1,717,986,919, because the int table-size doubling loop overflows past 2^30 to Integer.MIN_VALUE and then 0 (also through CharArraySet(Collection), CharArrayMap(Map) and StopFilter.makeStopSet(List) by size(); since 2.3.0)

Finding LFV-1162 · Area: analysis / CharArraySet and CharArrayMap capacity constructors (stop-word, keyword and stem-override sets and maps) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1162.html](../../bugs/LFV-1162.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call new CharArraySet(858_993_460, false), or new CharArrayMap(858_993_460, false), CharArraySet(Collection, boolean), CharArrayMap(Map, boolean) or StopFilter.makeStopSet(List, boolean) with a collection of that size(). You expect the constructor to return or throw, as it does one value lower, where it fails at once with OutOfMemoryError. Instead it never returns: the loop doubles an int table size while startSize + (startSize &gt;&gt; 2) is larger, so above 2^30 the size goes to Integer.MIN_VALUE, then 0, and stays 0, and the thread spins forever before allocating anything; the same happens for a startSize at or below -1,717,986,919, where the sum wraps to a large positive value.

## Expected vs actual

- Expected: `new CharArraySet(858_993_460, false)` returns or throws, as 858,993,459 does (`OutOfMemoryError` after 27 ms).
- Actual: `AssertionError: new CharArraySet(858_993_460, false) still running after 10 s`; the boundary probes show the thread RUNNABLE at `CharArrayMap.<init>(CharArrayMap.java:58)`.

## Files

- [src/org/apache/lucene/analysis/TestCharArraySetCapacityOverflow.java](src/org/apache/lucene/analysis/TestCharArraySetCapacityOverflow.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.TestCharArraySetCapacityOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestCharArraySetCapacityOverflow.java](TestCharArraySetCapacityOverflow.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
