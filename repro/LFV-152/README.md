# IndexWriter opened from a reader's IndexCommit still rejects an index by its created major ('createdVersionMajor must be &gt;= 9, got: 8'), although DirectoryReader.open, OpenMode.APPEND and a listCommits commit accept the same commit under the per-segment version check of GITHUB#14607

Bug LFV-152 · Area: index / IndexWriter open from a reader commit (version gate) · Priority: low · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You have an index that Lucene 8.x created and 9.x then fully re-indexed in place, so every segment is 9.x; since GITHUB#14607 (released in 10.4.0) Lucene 10.x and main open it normally for reading and with an IndexWriter in APPEND mode. You expect new IndexWriter(dir, new IndexWriterConfig.setIndexCommit(reader.getIndexCommit)), which the setIndexCommit javadoc documents for near-real-time readers, to open it as well. Instead it throws IllegalArgumentException 'createdVersionMajor must be &gt;= 9, got: 8', because this one constructor branch still checks the index's creation major, the rule #14607 replaced on every other open path with a check of each segment's version.

## Expected vs actual

- Expected: You expect new IndexWriter(dir, new IndexWriterConfig.setIndexCommit(reader.getIndexCommit)), which the setIndexCommit javadoc documents for near-real-time readers, to open it as well.
- Actual: It throws IllegalArgumentException 'createdVersionMajor must be &gt;= 9, got: 8', because this one constructor branch still checks the index's creation major, the rule #14607 replaced on every other open path with a check of each segment's version.

## Files

- [src/org/apache/lucene/index/TestWriterFromReaderCommitCreatedMajor.java](src/org/apache/lucene/index/TestWriterFromReaderCommitCreatedMajor.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.index.TestWriterFromReaderCommitCreatedMajor
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
