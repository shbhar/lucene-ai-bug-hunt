# CheckIndex.checkImpacts compares each impact of a level only with the level's first impact (prevFreq and prevNorm are never advanced in the loop), so an impact list that is not sorted, such as (1,1),(3,3),(2,2), passes CheckIndex.testPostings and the codec test framework's impacts checks (since 8.0.0)

Finding LFV-1272 · Area: index / CheckIndex postings check (per-level impacts order) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1272.html](../../bugs/LFV-1272.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run CheckIndex.testPostings on a segment whose postings format returns, for a term, one impacts level (freq, norm) = (1,1),(3,3),(2,2), which breaks the rule documented on Impacts.getImpacts that a level's impacts are sorted by increasing frequency and increasing unsigned norm. You expect the CheckIndexException 'Impacts are not ordered or contain dups, got (3,3) then (2,2)', the error that CheckIndex.checkImpacts exists to throw. Instead testPostings reports no error: checkImpacts sets prevFreq and prevNorm to the first entry and never advances them, so every entry is compared only with (1,1), and both (3,3) and (2,2) are above it.

## Expected vs actual

- Expected: `CheckIndex.checkImpacts` throws `CheckIndexException: Impacts are not ordered or contain dups, got (3,3) then (2,2)`, as it does with the fix.
- Actual: No exception is thrown, so `expectThrows` fails with "Expected exception CheckIndexException but no exception was thrown", with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestCheckImpactsOrder.java](src/org/apache/lucene/index/TestCheckImpactsOrder.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestCheckImpactsOrder
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestCheckImpactsOrder.java](TestCheckImpactsOrder.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
