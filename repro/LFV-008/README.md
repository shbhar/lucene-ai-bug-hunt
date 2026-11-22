# Removing a document's soft-delete value brings a hard-deleted document back

Finding LFV-008 · Area: index / soft deletes · Priority: medium · Impact: data corruption · Novelty: known upstream

Finding page: [../../bugs/LFV-008.html](../../bugs/LFV-008.html)

AI-assisted finding, confirmed by automated checks. Upstream status: Fix in progress, by diwu-sf (open PR #17003; an independent fix, later than our listing) on 2026-11-06 (https://github.com/apache/lucene/pull/17003). Open PR #17003 (diwu-sf, opened 2026-11-06; a draft with its last commit of 2026-11-09, no milestone and no review when read on 2026-11-18) stops applySoftDeletes from undeleting hard-deleted documents. Its author made it a draft again on 2026-11-10 and wrote that they are still thinking about whether one of its commits is the right move. With the two source classes that the pull request changes (PendingSoftDeletes, SoftDeletesDirectoryReaderWrapper) in front, our minimal repro and the rejected-document test pass on main 1cfa86310f76 and on branch_11x 89e68e0f3a50, with and without assertions; without them they fail on both (run 2026-11-17). The PR is not an earlier report of it: this row was listed publicly on 2026-10-27 and the PR came later and independently; the owner linked this row there on 2026-11-07. This site files nothing upstream.

## What it shows

With a soft-deletes field configured, you hard-delete a document and then clear its soft-delete value. You expect the document to stay deleted. Instead PendingSoftDeletes.applySoftDeletes sets its live bit again, so it becomes visible; softDelCount can reach -1, which wedges the writer and gives a corrupt commit or merge.

The test shows that with a soft-deletes field (b soft-deleted), hard-deleting doc a and then removing a's soft value with `tryUpdateDocValue(reader, 0, null)` makes a live again.

## Expected vs actual

Expected: `count(id:a)` is 0 on the next NRT reader (a hard-deleted doc stays deleted, `IndexWriterConfig#setSoftDeletesField`). Actual (`-ea` and `-da`): `expected:<0> but was:<1>`.

## Files

- [src/org/apache/lucene/index/TestSoftValueRemovalRevivesHardDeletedDoc.java](src/org/apache/lucene/index/TestSoftValueRemovalRevivesHardDeletedDoc.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): still reproduces.
- Tested on Lucene main 645d4fe14a20 (a commit of 2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
- Known upstream in part: in the review of the change that introduced this code (LUCENE-8298, apache/lucene#9345), Adrien Grand wrote on 2018-05-09 that "the ability to un-hard-delete is a bit scary to me because of the documents that got marked as deleted because they failed indexing", about a patch that already had this branch; it was merged unchanged. The comment names the hazard on a patch in review; it does not say that merged or released Lucene does it. What this folder covers: the test shows that released code does it for a document the application hard-deleted (the soft-delete count of -1, the merge that keeps the document and the document that IndexWriter rejected are on the finding page). Run on 2026-11-18 on Lucene main 1cfa86310f76, with and without assertions: 1 test found, 1 test failed (expected:&lt;0&gt; but was:&lt;1&gt;).
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
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestSoftValueRemovalRevivesHardDeletedDoc
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
