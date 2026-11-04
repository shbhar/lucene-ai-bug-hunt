# CheckIndex.checkImpacts' cross-level scan advances the higher level's cursor once per undominated lower-level impact and never re-tests that impact, so level 1 [(1,1),(2,2)] over level 0 [(1,1),(5,5)] passes CheckIndex.testPostings although no level-1 impact dominates (5,5), while level 1 [(1,1)] alone is rejected (since 8.0.0)

Finding LFV-1273 · Area: index / CheckIndex postings check (impacts cross-level dominance scan) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1273.html](../../bugs/LFV-1273.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index two documents in field f ('a' and 'a a a a a', so term a has (freq, norm) (1,1) and (5,5)) and run CheckIndex.testPostings at the slow-check level over a reader whose impacts for a report level 0 [(1,1),(5,5)] and level 1 [(1,1),(2,2)], both valid to the end of the postings, as a broken custom postings format could. You expect the error that CheckIndex's own comment and message describe, 'Found impact (5,5) on level 0 but no impact on level 1 triggers a better score', because no level-1 impact has freq &gt;= 5 and level 1 therefore under-states document 1. Instead status.error is null on main (-ea and -da) and on 10.6.0, 10.5.2 and 9.12.3 (-da): after (5,5) misses (1,1) the scan moves the level-1 cursor to (2,2) and goes on to the next level-0 impact (there is none), so (5,5) is never compared with (2,2), while level 1 [(1,1)] alone gets the expected error.

## Expected vs actual

- Expected: `checkImpacts` throws `CheckIndexException`: "Found impact (5,5) on level 0 but no impact on level 1 triggers a better score: freqs=[1, 2] norms=[1, 2]", as it does with the fix.
- Actual: `checkImpacts` returns normally, so the test fails with "Expected exception CheckIndexException but no exception was thrown", with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestCheckImpactsAcrossLevels.java](src/org/apache/lucene/index/TestCheckImpactsAcrossLevels.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestCheckImpactsAcrossLevels
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestCheckImpactsAcrossLevels.java](TestCheckImpactsAcrossLevels.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
