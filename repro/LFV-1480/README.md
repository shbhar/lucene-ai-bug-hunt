# DirectoryTaxonomyWriter keeps a closed ReaderManager installed when closing it fails at the end of the cache fill, so addCategory on the still-open writer throws AlreadyClosedException whenever it next needs its internal reader, at every later cache eviction (since 4.0.0)

Finding LFV-1480 · Area: facet / taxonomy writer (DirectoryTaxonomyWriter cache fill, perhapsFillCache, and the lifecycle of its internal near-real-time ReaderManager after a failed close) · Priority: low · Impact: minor · Novelty: new

Finding page: [https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1480.html](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1480.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You reopen an existing taxonomy with a DirectoryTaxonomyWriter and add categories, committing as you go; at its 11th cache miss the writer has read every category into its cache and closes its internal near-real-time reader, and that close fails once with an IOException (here the close of a file of a segment that a merge has already retired), so that addCategory call throws. You expect a writer that is still open (it was never closed or rolled back, and its IndexWriter is open) not to keep a reader manager that is already closed: it should drop it, as it does when the close succeeds, so that later addCategory calls do not fail because of it. Instead the closed ReaderManager stays installed for the rest of the writer's life, and every later addCategory whose cache insertion evicts entries throws AlreadyClosedException ('this ReferenceManager is closed') after it has already indexed the category's document (the second call after the failure in the shipped test, which uses a 12-entry cache), until the writer is closed or rolled back and reopened.

## Expected vs actual

- Expected: `addCategory(c12)` on the still-open writer returns 12.
- Actual: `AlreadyClosedException: this ReferenceManager is closed` from `refreshReaderManager` (`DirectoryTaxonomyWriter.java:505`), at line 75 of the test.

## Files

- [src/org/apache/lucene/facet/taxonomy/TestTaxonomyWriterFailedCacheFillClose.java](src/org/apache/lucene/facet/taxonomy/TestTaxonomyWriterFailedCacheFillClose.java) (JUnit test)

## Run it

- Tested on Lucene main 82cf7465bb8f (a commit of 2026-11-26): still reproduces.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
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
     execute --select-class org.apache.lucene.facet.taxonomy.TestTaxonomyWriterFailedCacheFillClose
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Files shown on the finding page

- [TestTaxonomyWriterFailedCacheFillClose.java](TestTaxonomyWriterFailedCacheFillClose.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
