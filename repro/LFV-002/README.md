# Merge doc-values carry-over (#16570) can lose an acknowledged update or close the writer on a duplicate delGen

Finding LFV-002 · Area: index / IndexWriter merge · Priority: medium · Impact: data corruption · Novelty: new

Finding page: [../../bugs/LFV-002.html](../../bugs/LFV-002.html)

AI-assisted finding, confirmed by automated checks. Upstream status: Fix in progress, by Jim Ferenczi (Lucene PMC; open PR #16988) on 2026-11-06 (https://github.com/apache/lucene/pull/16988). Open PR #16988 (jimczi, opened 2026-11-06; milestone 10.6.1; open as read on 2026-11-21) says it was found via this list and names LFV-002. It clamps the delGen of the carried-over on-disk doc-values value in IndexWriter's merge to min(minResidual - 1, completedDelGen) and adds our minimal repro as TestMergeDocValuesCarryOverRace with one added setting (setMaxDocValuesOverlays(16)). Our minimal repro fails on main 781025d6fe32 and passes there with the PR's change applied, with and without assertions (runs of 2026-11-14). With the same change of one expression applied to IndexWriter of branch_11x 5d7ca54ba503 it passes with the overlays setting at its default, at 0, at 1 and at 8, with and without assertions (runs of 2026-11-19). This site files nothing upstream.

## What it shows

While a merge runs, a document in a merging segment gets an older doc-values update that is still being applied and a newer update to the same segment and field that finishes first. You expect the newest value to win on the merged segment. Instead the merge gives the carried-over on-disk value a delGen at or above the in-flight packet's, so the older value silently wins and survives commit and reopen, or, with assertions on and equal gens, a duplicate-delGen assertion closes the writer and discards uncommitted changes.

The test shows that during a merge, a doc-values update whose packet is still being applied loses to an older value carried over from disk, when a newer packet on the same segment and field finishes first (#16570 carry-over path, default config).

## Expected vs actual

Expected the k0 document's last update `n=2` after the merge, commit and reopen; actual: the older `n=1` (`expected:<2> but was:<1>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/index/TestMergeDocValuesCarryOverRace.java](src/org/apache/lucene/index/TestMergeDocValuesCarryOverRace.java) (JUnit test)

## Run it

- Tested on Lucene main 2cf3f5776d9e (a commit of 2026-11-19): reproduced, with assertions on and off.
- Tested on Lucene branch_11x 75eea467ac52 (a commit of 2026-11-19): reproduced, with assertions on and off (set V=11.0.0-SNAPSHOT).
- Tested on Lucene main 645d4fe14a20 (a commit of 2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
- The test leaves maxDocValuesOverlays at its default: 8 on main and on branch_11x, 0 (off) in 10.6.0. Runs of 2026-11-19: with the 10.6.0 release (the three 10.6.0 jars from Maven Central, org.apache.lucene: lucene-core, lucene-codecs and lucene-test-framework, in place of the three $LUCENE jars; V=10.6.0) the test as shipped passes, with assertions on and off; with one line added after new IndexWriterConfig(), .setMaxDocValuesOverlays(1), it fails there with the same message; on main and on branch_11x it also fails with .setMaxDocValuesOverlays(0). The test pins the race with latches: a failing run shows the outcome of that schedule, not how often a workload meets it.
- This finding was re-run on Lucene main 11c8b6ea99ae on 2026-11-23: still reproduces.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
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
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestMergeDocValuesCarryOverRace
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
