# DirectIODirectory's inputs never override IndexInput#isEfficientFor, so the #16883/#16905 input sharing lets merges of NRT-open segments skip direct I/O and lets later sequential search reads go through the merge's direct-I/O input

Bug LFV-585 · Area: misc / store: DirectIODirectory x core codecs merge-time re-open (the IndexInput#isEfficientFor gate in Lucene99FlatVectorsReader and Lucene90CompressingStoredFieldsReader, #16883/#16905) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open an index with DirectIODirectory over the default MMapDirectory, keep an NRT reader open and let IndexWriter merge the segments that reader holds. You expect the merge to read their vector (.vec) and stored fields (.fdt) data with direct I/O while searches go through the delegate, as DirectIODirectory's javadoc says and as #16905 (which names this directory as the case it restores) intends. Instead the codec readers reuse the input the search already holds whenever that input says it is efficient for the merge, DirectIODirectory's inputs never answer that question (the default answer is yes), so the merge reads through the page cache, and with an advising delegate a sequential search read after the merge goes through the merge's direct-I/O input.

## Expected vs actual

- Expected: You expect the merge to read their vector (.vec) and stored fields (.fdt) data with direct I/O while searches go through the delegate, as DirectIODirectory's javadoc says and as #16905 (which names this directory as the case it restores) intends.
- Actual: The codec readers reuse the input the search already holds whenever that input says it is efficient for the merge, DirectIODirectory's inputs never answer that question (the default answer is yes), so the merge reads through the page cache, and with an advising delegate a sequential search read after the merge goes through the merge's direct-I/O input.

## Files

- [src/org/apache/lucene/misc/store/TestDirectIOMergeInputSharing.java](src/org/apache/lucene/misc/store/TestDirectIOMergeInputSharing.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.store.TestDirectIOMergeInputSharing
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
