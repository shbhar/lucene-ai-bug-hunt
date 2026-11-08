# ReferenceManager.close() skips the afterClose() cleanup hook when releasing the current reference throws, and every later close() does nothing, so a subclass's afterClose() never runs (since 3.6.0)

Finding LFV-1290 · Area: core / search: ReferenceManager (close() and the afterClose() hook of custom subclasses) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1290.html](../../bugs/LFV-1290.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You write a ReferenceManager subclass that frees its own resources in afterClose(), which the javadoc describes as 'Called after close(), so subclass can free any resources', and you close it while the final release of the current reference throws an IOException (for example a reader's ClosedListener fails). You expect close() to report the IOException and still run afterClose(), or at least to let a second close() run it. Instead close() throws without calling afterClose(), and because it has already set the current reference to null, every later close() returns at once, so the hook never runs and its resources are never freed.

## Expected vs actual

- Expected: `close()` throws the `IOException` and `afterClose()` has run once, as with the fix.
- Actual: `java.lang.AssertionError: expected:<1> but was:<0>`: `afterClose()` never ran, even after the second `close()`.

## Files

- [src/org/apache/lucene/search/TestReferenceManagerAfterCloseOnFailedRelease.java](src/org/apache/lucene/search/TestReferenceManagerAfterCloseOnFailedRelease.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestReferenceManagerAfterCloseOnFailedRelease
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestReferenceManagerAfterCloseOnFailedRelease.java](TestReferenceManagerAfterCloseOnFailedRelease.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
