# DirectoryTaxonomyWriter's constructor never closes or rolls back the IndexWriter it has opened when a later step of the constructor fails with an I/O error, so the taxonomy stays write-locked and every later DirectoryTaxonomyWriter on it in the same JVM fails with LockObtainFailedException

Bug LFV-558 · Area: facet / taxonomy writer (DirectoryTaxonomyWriter construction, failure cleanup) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open a taxonomy with new DirectoryTaxonomyWriter(dir) and the constructor throws an IOException, because a file read failed after it had already opened its internal IndexWriter (while reading the latest commit of an existing taxonomy, or while opening a reader to add the root category of a new one); once the error has passed you try again. You expect the retry to work, because a constructor that throws hands the caller nothing to close and so must release what it acquired, as IndexWriter's own constructor does. Instead the constructor has no cleanup after openIndexWriter, so the unreachable IndexWriter keeps the taxonomy's write lock and every later DirectoryTaxonomyWriter on that directory in the same JVM fails with LockObtainFailedException 'Lock held by this virtual machine'.

## Expected vs actual

- Expected: You expect the retry to work, because a constructor that throws hands the caller nothing to close and so must release what it acquired, as IndexWriter's own constructor does.
- Actual: The constructor has no cleanup after openIndexWriter, so the unreachable IndexWriter keeps the taxonomy's write lock and every later DirectoryTaxonomyWriter on that directory in the same JVM fails with LockObtainFailedException 'Lock held by this virtual machine'.

## Files

- [src/org/apache/lucene/facet/taxonomy/directory/TestDirectoryTaxonomyWriterCtorFailure.java](src/org/apache/lucene/facet/taxonomy/directory/TestDirectoryTaxonomyWriterCtorFailure.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.taxonomy.directory.TestDirectoryTaxonomyWriterCtorFailure
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
