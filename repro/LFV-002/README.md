# Merge doc-values carry-over (#16570) can lose an acknowledged update or close the writer on a duplicate delGen

Bug LFV-002 · Area: index / IndexWriter merge · Priority: medium · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-002.html](../../bugs/LFV-002.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

While a merge runs, a document in a merging segment gets an older doc-values update that is still being applied and a newer update to the same segment and field that finishes first. You expect the newest value to win on the merged segment. Instead the merge gives the carried-over on-disk value a delGen at or above the in-flight packet's, so the older value silently wins and survives commit and reopen, or, with assertions on and equal gens, a duplicate-delGen assertion closes the writer and discards uncommitted changes.

## Expected vs actual

- Expected: doc 0 = 200 on the merged segment (`{0=200, 1=301, 2=302}`), under `-ea` and `-da`, and the writer stays open.
- Actual: 3 of 6 tests fail on main: doc 0 = 100 under `-ea` and `-da`, except that with `-ea` the tie test fails on `duplicate delGen=4`, which closes the writer.

## Files

- [src/fvverify/TestMergeCommitDVCarryOverRace.java](src/fvverify/TestMergeCommitDVCarryOverRace.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main b83101cdd2 (2026-10-23): reproduced.
- Recorded outcome of run 1 on main a9779ef3c193: 5 test(s) run, 2 failed (testAdjacentGens, testLostUpdate).
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
java -ea -esa -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class fvverify.TestMergeCommitDVCarryOverRace
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [CarryOverRace.java](CarryOverRace.java)
- [test-main-cad6f4fad6.diff](test-main-cad6f4fad6.diff)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
