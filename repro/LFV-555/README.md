# DirectoryTaxonomyWriter.close skips its own cleanup when its commit fails: after an I/O error that closes the internal IndexWriter every close throws and the writer's near-real-time reader keeps its segment files open, and after an fsync error inside close the IndexWriter and the taxonomy write lock stay held until close is retried or rollback is called (every release since 5.0.0)

Bug LFV-555 · Area: facet / taxonomy writer: DirectoryTaxonomyWriter.close (failure cleanup) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add categories to a DirectoryTaxonomyWriter, a commit fails with an I/O error (for example a full disk while the new segment is written), and you then call close, whose javadoc says it 'Frees used resources as well as closes the underlying IndexWriter'. You expect close to release the writer's resources even when it throws, as IndexWriter.close has done since LUCENE-4246. Instead close is 'commit; indexWriter.close; doClose;' with no try/finally, so it throws AlreadyClosedException from its own commit on every call and never closes its internal near-real-time reader, whose segment file stays open; when the error is an fsync inside close, the internal IndexWriter and the taxonomy's write lock also stay held until close is retried or rollback is called.

## Expected vs actual

- Expected: You expect close to release the writer's resources even when it throws, as IndexWriter.close has done since LUCENE-4246.
- Actual: close is 'commit; indexWriter.close; doClose;' with no try/finally, so it throws AlreadyClosedException from its own commit on every call and never closes its internal near-real-time reader, whose segment file stays open; when the error is an fsync inside close, the internal IndexWriter and the taxonomy's write lock also stay held until close is retried or rollback is called.

## Files

- [src/org/apache/lucene/facet/taxonomy/directory/TestDirectoryTaxonomyWriterCloseAfterFailure.java](src/org/apache/lucene/facet/taxonomy/directory/TestDirectoryTaxonomyWriterCloseAfterFailure.java) (JUnit test)

## Run it

- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.taxonomy.directory.TestDirectoryTaxonomyWriterCloseAfterFailure
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
