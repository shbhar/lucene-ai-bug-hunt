# A merge that opts into the experimental partitioned merge (OneMerge.isPartitioned(), @lucene.experimental, which no merge policy in Lucene's main sources uses) does not checksum a doc-values file that its wrapForMerge reader, a hand-written FilterCodecReader, takes from another index through the producer getter getDocValuesReader() (@lucene.internal): a file that is already damaged is merged without CorruptIndexException, where the ordinary merge of the same readers refuses it (branch_11x and main, both unreleased; since #16571)

Finding LFV-1532 · Area: index / merges: the partitioned merge of #16571 (IndexWriter.multiOutputMergeMiddle, FilterCodecReader.checkIntegrity(OneMerge), MergePolicy.OneMerge wrapForMerge and markInputsVerified, CodecUtil.checksumEntireFile) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1532.html](../../bugs/LFV-1532.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You write a merge policy that uses the experimental partitioned merge of Lucene 11.0 (one merge that writes several segments), and your merge wraps each segment in a FilterCodecReader that takes its doc values from a second index. You expect what an ordinary merge of the same readers does on the same builds and on 10.6.0: when the doc-values file of the second index is already damaged, the merge reads the whole file, finds that the checksum does not match and stops with CorruptIndexException. Instead the partitioned merge checksums the files of the wrapped segment only, records its inputs as verified and from then on checks only the footer of the other file, so the damage (which Lucene did not cause) goes unnoticed: in the runs (256 documents, one flipped bit) the merge completed without an error, one value in the merged index was wrong and CheckIndex reported the merged index clean.

## Expected vs actual

- Expected: `forceMerge(1)` throws `CorruptIndexException`, as the ordinary merge of the same readers does (the writer then closes itself and the index stays as it was).
- Actual: The partitioned merge completes: `Expected exception CorruptIndexException but no exception was thrown`, with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestPartitionedMergeChecksumsWrapperProducers.java](src/org/apache/lucene/index/TestPartitionedMergeChecksumsWrapperProducers.java) (JUnit test)

## Run it

- Tested on Lucene main 596ef11ddf96 (a commit of 2026-11-17): reproduced, with assertions on and off.
- Tested on Lucene branch_11x 89e68e0f3a50 (a commit of 2026-11-17): reproduced, with assertions on and off (11.0.0-SNAPSHOT).
- What each build does: the test fails the same way on Lucene main and on the 11.0 release branch (branch_11x), with and without assertions. With the jars of the 10.6.0 release the test does not compile (4 errors, all about the partition API: the class DocRangePartitions does not exist there, and isPartitioned and getDocRangePartitions override nothing), because no release has the partitioned merge. What the test does: it writes two documents with one numeric doc-values field (0 and 1) into one segment, copies the index and flips the lowest bit of the last byte before the footer of the copy's doc-values data file, the file's one data byte; it then force-merges the first index with a merge policy of its own, whose one merge is partitioned into two outputs and wraps the segment in a FilterCodecReader that hands out the copy's doc-values producer, and expects forceMerge(1) to throw CorruptIndexException. It fails with junit.framework.AssertionFailedError: Expected exception CorruptIndexException but no exception was thrown: the merge of the damaged file completed. The damage is made by the test; Lucene damages nothing. The test draws no random value and its outcome does not depend on the seed. On a checkout of the 11.0 branch set V=11.0.0-SNAPSHOT. The control for a release: delete the two overrides isPartitioned and getDocRangePartitions from the test, which makes the merge an ordinary one, and put the three 10.6.0 jars from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework) in place of the three $LUCENE jars: the test then passes, with and without assertions, because the ordinary merge throws CorruptIndexException; it passes in that form on main and on the 11.0 branch as well. The file at the top of this folder, which the finding page shows, is the same test; for the Gradle alternative at the end of this page the module is lucene/core and the file goes to lucene/core/src/test/org/apache/lucene/index/.
- This finding was re-run on Lucene main b5a4546a5964 on 2026-11-22: still reproduces.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with junit.framework.AssertionFailedError: Expected exception CorruptIndexException but no exception was thrown.
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

# run 1: assertions on, seed 5EED0841
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=5EED0841 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestPartitionedMergeChecksumsWrapperProducers
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestPartitionedMergeChecksumsWrapperProducers.java](TestPartitionedMergeChecksumsWrapperProducers.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
