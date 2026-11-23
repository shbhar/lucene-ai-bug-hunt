# IndexMergeTool cannot merge source indexes in which a field carries the soft-deletes role (from 7.4.0) or the parent-field role (from 9.10.0): its writer configuration names neither field and the command line has no option for them, so IndexWriter.addIndexes throws IllegalArgumentException and no document is merged

Finding LFV-1548 · Area: misc / index tools (IndexMergeTool.main: the writer configuration of the merged index and the tool's option list); IndexWriter.addIndexes field-role checks · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1548.html](../../bugs/LFV-1548.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run the command-line tool IndexMergeTool on two indexes that your application wrote with a soft-deletes field (IndexWriterConfig#setSoftDeletesField and one softUpdateDocument each) and expect one merged index: the tool's usage text says it 'Merges source indexes 'index1' .. 'indexN' into 'mergedIndex'' and names no condition on how the sources were written. Instead the tool stops with IllegalArgumentException, 'this index has [soft] as soft-deletes already but soft-deletes field is not configured in IWC', and merges nothing: it opens the merged index with a writer configuration that names no soft-deletes field, and it has no option to name one. Two indexes written with a parent field (IndexWriterConfig#setParentField) are refused the same way ('can't add field [parent] as parent document field; this IndexWriter has no parent document field configured'), while IndexWriter.addIndexes into a writer that names the same field merges the same indexes.

## Expected vs actual

- Expected: A merged index with 2 live documents (the usage text: "Merges source indexes 'index1' .. 'indexN' into 'mergedIndex'").
- Actual: `java.lang.IllegalArgumentException: this index has [soft] as soft-deletes already but soft-deletes field is not configured in IWC` from `IndexWriter.addIndexes`, called at `IndexMergeTool.main(IndexMergeTool.java:118)`; no document is merged.

## Files

- [src/org/apache/lucene/misc/TestIndexMergeToolSoftDeletes.java](src/org/apache/lucene/misc/TestIndexMergeToolSoftDeletes.java) (JUnit test)

## Run it

- Tested on Lucene main 11c8b6ea99ae (a commit of 2026-11-23): still reproduces.
- Tested on Lucene main ec3b3cca8f10 (a commit of 2026-11-18): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.TestIndexMergeToolSoftDeletes
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestIndexMergeToolSoftDeletes.java](TestIndexMergeToolSoftDeletes.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
