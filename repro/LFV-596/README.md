# IndexWriter.forceApply marks a re-resolving update packet finished outside the IndexWriter lock when its re-iteration matches no segment, so a merge that commits in that gap skips the packet's already-resolved doc-values updates and nobody applies them again: an acknowledged updateNumericDocValue/updateDocValues is lost and softUpdateDocument leaves two live versions of the document, also after commit (since 7.0.0)

Finding LFV-596 · Area: index / IndexWriter frozen update packets vs. merge carry-over (forceApply, commitMergedDeletesAndUpdates, BufferedUpdatesStream) · Priority: medium · Impact: data corruption · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You update a document's doc-values field (updateNumericDocValue, updateDocValues) or replace a document with softUpdateDocument while background merges run; IndexWriter returns a sequence number, so every later NRT reader and commit should show the change. If the update packet must re-resolve because a merge committed meanwhile and that merge's result was dropped as fully deleted, IndexWriter.forceApply finds no segment left, leaves the IndexWriter lock and only then marks the packet finished; a second merge that commits in that gap treats the packet as still running, skips its already-resolved update on the merged-away segment, and nobody applies it again. The update is silently lost (id 3 keeps value 30 instead of 7, also after commit and reopen), and with softUpdateDocument the old version is never soft-deleted, so two live versions of one document remain.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the reported behaviour is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/index/TestEmptyReResolveLosesDocValuesUpdate.java](src/org/apache/lucene/index/TestEmptyReResolveLosesDocValuesUpdate.java) (JUnit test)
- [src/org/apache/lucene/index/TestEmptyReResolveLosesDocValuesUpdate8x.java](src/org/apache/lucene/index/TestEmptyReResolveLosesDocValuesUpdate8x.java) (JUnit test)

## Run it

- Tested on Lucene main 11c8b6ea99ae (a commit of 2026-11-23): still reproduces.
- Tested on Lucene main a9779ef3c193 (a commit of 2026-10-26): reproduced.
- The javac line below compiles the two files under src/ together, and they are the same test for two ranges of Lucene versions. As printed, the line stops on a build of Lucene main or of branch_11x and with the jars of the 10.6.0 release (TestEmptyReResolveLosesDocValuesUpdate8x.java does not compile there), and both runs then find no test. Name one file in the javac line, in place of $(find src -name '*.java'), and leave out the run of the other class. On a build of main or of branch_11x (there V=11.0.0-SNAPSHOT) and with the 10.6.0 jars (Maven Central, org.apache.lucene, in place of the three $LUCENE jars; V=10.6.0) that file is src/org/apache/lucene/index/TestEmptyReResolveLosesDocValuesUpdate.java, the class of run 1: run on 2026-11-22 on a build of main b5a4546a5964, on a build of branch_11x 262e019500ec and with the 10.6.0 jars, with assertions on and off, run 1 reports 2 tests found and 2 failed (testDocValuesUpdateLostWhenEmptyIterationRacesMergeCommit with 'acknowledged update of id 3 (seqNo 14) is visible expected:&lt;7&gt; but was:&lt;30&gt;', testSoftUpdateLosesSoftDeleteWhenEmptyIterationRacesMergeCommit with 'live docs (one version of id 3): [id=3 dv=30][id=3 dv=7] expected:&lt;1&gt; but was:&lt;2&gt;'); with the jars of 9.12.3 the same file fails 2 of 2 too (run the same day with assertions on). TestEmptyReResolveLosesDocValuesUpdate8x.java, the class of run 2, imports MockAnalyzer and LuceneTestCase from org.apache.lucene.analysis and org.apache.lucene.util, their packages in the releases up to 9.0.0, and reads stored fields through the LeafReader: named alone in the javac line, with the jars of 9.0.0 and of 8.11.4 (V=9.0.0, V=8.11.4), run 2 fails 2 of 2 with the same two messages (run the same day with assertions on).
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
     execute --select-class org.apache.lucene.index.TestEmptyReResolveLosesDocValuesUpdate

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestEmptyReResolveLosesDocValuesUpdate8x
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

Formal models and scripts this finding's part of the hunt used: [../../models/frozen-updates-apply-merge-carryover-tla/](../../models/frozen-updates-apply-merge-carryover-tla/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
