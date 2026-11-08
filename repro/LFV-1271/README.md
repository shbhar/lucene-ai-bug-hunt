# LRUQueryCache.setSkipCacheFactor accepts NaN, 0, negative values and values below 1, which the constructor rejects with IllegalArgumentException, so a bad runtime setting is silently applied to query-cache admission (since 10.2.0)

Finding LFV-1271 · Area: core / search: LRUQueryCache (skip-cache factor setter) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1271.html](../../bugs/LFV-1271.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an LRUQueryCache and later change its skip-cache factor at runtime with setSkipCacheFactor, passing NaN (for example from a failed parse), 0 or 0.5. You expect the IllegalArgumentException 'skipCacheFactor must be no less than 1' that the constructor throws for the same value (its check carries the comment '// NaN &gt;= 1 evaluates false'). Instead the setter stores the value silently, and the cache's admission test 'cost / skipCacheFactor &gt; leadCost' then runs with it: NaN or a negative value turns the skip heuristic off, 0 stops the caching of every clause that matches documents, and a value in (0, 1) can skip clauses that every valid factor caches; search hits stay correct.

## Expected vs actual

- Expected: `setSkipCacheFactor(Float.NaN)` throws `IllegalArgumentException`, as the constructor does for NaN.
- Actual: `AssertionFailedError: Expected exception IllegalArgumentException but no exception was thrown`: NaN is stored.

## Files

- [src/org/apache/lucene/search/TestLRUQueryCacheSkipCacheFactorSetter.java](src/org/apache/lucene/search/TestLRUQueryCacheSkipCacheFactorSetter.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestLRUQueryCacheSkipCacheFactorSetter
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestLRUQueryCacheSkipCacheFactorSetter.java](TestLRUQueryCacheSkipCacheFactorSetter.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
