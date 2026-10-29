# Merge doc-values carry-over (#16570) can lose an acknowledged update or close the writer on a duplicate delGen

Bug LFV-002 · Area: index / IndexWriter merge · Priority: medium · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-002.html](../../bugs/LFV-002.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

While a merge runs, a document in a merging segment gets an older doc-values update that is still being applied and a newer update to the same segment and field that finishes first. You expect the newest value to win on the merged segment. Instead the merge gives the carried-over on-disk value a delGen at or above the in-flight packet's, so the older value silently wins and survives commit and reopen, or, with assertions on and equal gens, a duplicate-delGen assertion closes the writer and discards uncommitted changes.

The test shows that during a merge, a doc-values update whose packet is still being applied loses to an older value carried over from disk, when a newer packet on the same segment and field finishes first (#16570 carry-over path, default config).

## Expected vs actual

Expected the k0 document's last update `n=2` after the merge, commit and reopen; actual: the older `n=1` (`expected:<2> but was:<1>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/index/TestMergeDocValuesCarryOverRace.java](src/org/apache/lucene/index/TestMergeDocValuesCarryOverRace.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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
     execute --select-class org.apache.lucene.index.TestMergeDocValuesCarryOverRace
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
