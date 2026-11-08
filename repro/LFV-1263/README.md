# On main, when the default terms dictionary (Lucene103BlockTreeTermsReader) fails to open, its cleanup calls close(), whose finally block runs fieldMap.clear() on a field the constructor has not assigned yet, so the real IOException or CorruptIndexException carries a spurious suppressed NullPointerException

Finding LFV-1263 · Area: codecs / default terms dictionary reader (Lucene103BlockTreeTermsReader) failed-open cleanup · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1263.html](../../bugs/LFV-1263.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open a DirectoryReader on an index written with the default codec, and opening one segment's terms dictionary fails, for example because the Directory throws an IOException while opening the .tip file. You expect that IOException to propagate, carrying as suppressed exceptions at most the failures of closing the files that were already open. On main it does propagate, but it carries a suppressed NullPointerException 'Cannot invoke IntObjectHashMap.clear() because this.fieldMap is null': the constructor's failure cleanup calls this.close(), and close() clears fieldMap in a finally block without a null check, although the constructor assigns fieldMap only near its end, after all the file opens and checks.

## Expected vs actual

- Expected: `DirectoryReader.open` throws the simulated `IOException` with no suppressed exceptions, as on 10.6.0 and with the fix.
- Actual: `AssertionError: expected:<[]> but was:<[java.lang.NullPointerException: Cannot invoke "org.apache.lucene.internal.hppc.IntObjectHashMap.clear()" because "this.fieldMap" is null]>`

## Files

- [src/org/apache/lucene/codecs/lucene103/blocktree/TestFailedOpenCleanup.java](src/org/apache/lucene/codecs/lucene103/blocktree/TestFailedOpenCleanup.java) (JUnit test)

## Run it

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

# run 1: assertions on, seed F764569C3580A402
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=F764569C3580A402 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.lucene103.blocktree.TestFailedOpenCleanup
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFailedOpenCleanup.java](TestFailedOpenCleanup.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
