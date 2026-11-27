# After a primary change, an NRT replica keeps a file it already copied from the old primary when the new primary's file has the same name, matching by name only, so the replica can serve and commit the wrong documents with CheckIndex clean

Finding LFV-241 · Area: replicator / NRT segment replication (CopyJob.transferAndCancel, ReplicaNode.newNRTPoint) · Priority: medium · Impact: data corruption · Novelty: new

Finding page: [https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-241.html](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-241.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run Lucene's NRT segment replication with failover: replica A is promoted to primary (a higher primaryGen) while replica B is still copying an NRT point from the old primary and has already finished one of its files, for example _1_1.liv. You expect B, once it has installed the new primary's NRT point, to search exactly the new primary's documents, because the module discards pre-copied data on a primary change and treats two files as the same only if name, header and footer match. Instead CopyJob._transferAndCancel keeps the old primary's finished temp file because the new primary's file has the same name, so B, at the new primary's version, shows [a0, b1] instead of [a0, a1], commits that state, and CheckIndex reports the index clean.

The test shows that after a failover (P1 crashes, replica A becomes P2), replica B keeps a .liv file it had already copied from P1 because P2's file has the same name, so B at P2's NRT version serves a mix of both primaries' deletes.

## Expected vs actual

Expected B to show P2's live docs `[a0, a1]`; actual `[a0, b1]` (`expected:<[a0, a1]> but was:<[a0, b1]>`), with `-ea` and `-da`; it passes with the name+metadata carry-over fix.

## Files

- [src/org/apache/lucene/replicator/nrt/TestNRTCopyAcrossPrimaryChange.java](src/org/apache/lucene/replicator/nrt/TestNRTCopyAcrossPrimaryChange.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (a commit of 2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
- The test calls new MatchAllDocsQuery() on line 83. On Lucene main (since 24d9e7d1a0d) and on branch_11x (since a6a26f9b251) that constructor is private (#16962, merged 2026-11-03), so on a build of either the javac line stops there: replace new MatchAllDocsQuery() with MatchAllDocsQuery.INSTANCE before you compile the test. The constant exists from Lucene 10.4.0 on, and with the jars of the 10.6.0 release the test compiles as shipped (put the five jars of that release, Maven Central, org.apache.lucene, in place of the five $LUCENE jars). Run on 2026-11-17, with assertions on and off: with the replacement the test fails 1 of 1 on a build of main 1cfa86310f76 (V=12.0.0-SNAPSHOT) and of branch_11x (89e68e0f3a50, V=11.0.0-SNAPSHOT), and as shipped it fails 1 of 1 with the 10.6.0 jars, each time with the failure stated above (expected [a0, a1], actual [a0, b1]); these runs had the four test jars and no other third-party jar on the class path.
- This finding was re-run on Lucene main 82cf7465bb8f on 2026-11-26: still reproduces (the repro adapted to an API change on this commit); in the commands below, V=12.0.0-SNAPSHOT on a build of main 82cf7465bb8f.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/replicator/build/libs/lucene-replicator-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.replicator.nrt.TestNRTCopyAcrossPrimaryChange
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
