# In no release (10.6.0 does not have it; 11.0.0 would be the first): on the 11.0 branch and main, with reader pooling off and no near-real-time reader opened from the writer, when IndexWriter fails to resolve buffered deletes and the clean-up after it fails too, only the clean-up failure is thrown and recorded as the tragic exception; the first failure is reported nowhere (10.6.0 throws the first failure with the clean-up failure as suppressed)

Finding LFV-1506 · Area: index / IndexWriter buffered deletes and updates resolution (forceApply and its clean-up finishApply; the applyUpdatesPacket tragic event) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1506.html](../../bugs/LFV-1506.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

In no release: 10.6.0 does not have it and 11.0.0 would be the first release with it (it is on branch_11x and main). You run an IndexWriter with reader pooling switched off, never open a near-real-time reader from that writer, and buffer a delete-by-query that fails when the writer finally runs it (for example a phrase query on a field indexed without positions), and while the writer cleans up after that failure, writing a segment's live docs fails too (for example the disk is full). You expect commit() to tell you why the delete failed, as every release tried from 7.0.0 to 10.6.0 does (since 7.4.0 with the clean-up failure attached as a suppressed exception). Instead, on the 11.0 branch and on main commit() throws only the clean-up's IOException and getTragicException() returns it too, so the failure that started it appears nowhere; the writer is closed in both versions and no data differs, so what the 11.0 branch and main lose is the reason.

## Expected vs actual

- Expected: `commit()` throws the query's `IllegalStateException`, as on 10.6.0 (there with the `.liv` `IOException` as suppressed).
- Actual: `commit()` throws only `java.io.IOException: cannot create _0_1.liv` (`expected IllegalStateException but got java.io.IOException` at line 53), with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestFailedDeleteByQueryIsReported.java](src/org/apache/lucene/index/TestFailedDeleteByQueryIsReported.java) (JUnit test)

## Run it

- Tested on Lucene main 1ae70c6e45b8 (a commit of 2026-11-19): still reproduces.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced.
- In no release: the test fails on Lucene main and on the 11.0 release branch (branch_11x daa54f2e0cd3, with and without assertions) and passes on the 10.6.0 release jars, so 11.0.0 would be the first release with it. What the test does: with reader pooling off (IndexWriterConfig.setReaderPooling(false)), no near-real-time reader opened from the writer and a directory that refuses to create a live-docs (.liv) file, it indexes one document, commits, buffers a delete by term and a delete by a PhraseQuery on a field indexed without positions, and expects commit() to throw the IllegalStateException of that query. Both failures fall into the one commit() call: the query fails when the buffered deletes are resolved, and the clean-up after it cannot write the segment's live docs. On main and branch_11x commit() throws only the IOException of the clean-up, so the test fails with 'Unexpected exception type, expected IllegalStateException but got java.io.IOException: cannot create _0_1.liv'. It uses no randomness.
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

# run 1: assertions on, seed 5A7E7801
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=4 -Dtests.seed=5A7E7801 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestFailedDeleteByQueryIsReported
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestFailedDeleteByQueryIsReported.java](TestFailedDeleteByQueryIsReported.java)

Formal models and scripts this finding's part of the hunt used: [../../models/success-flag-refactors-cleanup-behaviour/](../../models/success-flag-refactors-cleanup-behaviour/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
