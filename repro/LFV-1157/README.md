# If closing the temporary reader of a doc-values update write throws, the segment's fieldInfosGen keeps pointing at a .fnm file the cleanup just deleted, so every later commit and NRT open fails until rollback()

Finding LFV-1157 · Area: index / IndexWriter doc-values update write path (ReadersAndUpdates.writeFieldUpdates error path, temporary reader of a just-merged segment) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1157.html](../../bugs/LFV-1157.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You update a doc-values field (for example with updateNumericDocValue) of a document whose segment is being merged, so the update is carried over to the merged segment, and the next commit writes it through a temporary reader whose close() throws an IOException; the commit fails and the writer stays open. You expect a second commit, once the fault is gone, to write the update, which is what the cleanup code itself prepares for ('so that a 2nd attempt to write will write to a new file'). Instead the segment's fieldInfosGen was already advanced to the new _N_&lt;gen&gt;.fnm, which the cleanup then deleted, so every later commit and NRT reader open fails on that missing file until rollback(), which throws away everything since the last commit.

## Expected vs actual

- Expected: The second `commit()` succeeds and writes the still-queued update.
- Actual: `java.nio.file.NoSuchFileException: _2_1.fnm` at the second `commit()` (line 95), with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestFieldUpdatesTempReaderCloseFailure.java](src/org/apache/lucene/index/TestFieldUpdatesTempReaderCloseFailure.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestFieldUpdatesTempReaderCloseFailure
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFieldUpdatesTempReaderCloseFailure.java](TestFieldUpdatesTempReaderCloseFailure.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
