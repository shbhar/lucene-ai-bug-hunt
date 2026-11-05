# SimpleRateLimiter.pause sleeps about 24.9 days for any remaining pause between about 2.5 and 24.9 days (up to 10x too long), because its int-overflow guard compares with 100000L * Integer.MAX_VALUE ns instead of 1000000L * Integer.MAX_VALUE ns (since 4.10.3)

Finding LFV-1297 · Area: core / store: RateLimiter.SimpleRateLimiter (IO throttling) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1297.html](../../bugs/LFV-1297.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You throttle IO with RateLimiter.SimpleRateLimiter and one call to pause needs, say, 3 days (259,200 bytes at 1 byte/s, or 3 days of bytes at 1 MB/s). You expect pause to sleep about 3 days, as it does for a 2-day pause and as it did up to 4.10.2. Instead the guard that is meant only to keep an int cast from overflowing fires at 100000L * Integer.MAX_VALUE ns (2.49 days) rather than at 1000000L * Integer.MAX_VALUE ns (24.86 days, where the cast really overflows), so pause asks Thread.sleep for Integer.MAX_VALUE ms and the thread sleeps 24.86 days, 8.3x the pause; every remaining pause between 2.49 and 24.86 days sleeps 24.86 days.

## Expected vs actual

- Expected: `Thread.sleep` is asked for at most 3 days (259,200,000,000,000 ns).
- Actual: `AssertionError: Thread.sleep asked for 2147483647000000 ns` (24.86 days).

## Files

- [src/org/apache/lucene/store/TestSimpleRateLimiterLongPause.java](src/org/apache/lucene/store/TestSimpleRateLimiterLongPause.java) (JUnit test)

## Run it

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
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.store.TestSimpleRateLimiterLongPause
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestSimpleRateLimiterLongPause.java](TestSimpleRateLimiterLongPause.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
