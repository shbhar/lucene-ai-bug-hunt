# FileDeleter.getUnrefedFiles throws NullPointerException when the FileDeleter was built with a null messenger, which its constructor javadoc allows ('Can be null if the user do not want debug infos'): its 'removing unreferenced file' message is the only one of the class's six messenger calls without a null check (since 9.6.0)

Finding LFV-1195 · Area: core / util: FileDeleter (getUnrefedFiles; @lucene.internal) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1195.html](../../bugs/LFV-1195.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You create a FileDeleter with a null messenger, which its constructor javadoc allows ('Can be null if the user do not want debug infos'), register a file with initRefCount("_0.si") and ask getUnrefedFiles() for the files that nobody references. You expect the set {"_0.si"}, as with a no-op messenger. Instead getUnrefedFiles() throws NullPointerException ('Cannot invoke BiConsumer.accept(Object, Object) because this.messenger is null'), because it sends its 'removing unreferenced file' message without the null check that the class's other five messenger calls have.

## Expected vs actual

- Expected: `getUnrefedFiles()` returns `{"_0.si"}`.
- Actual: `NullPointerException: Cannot invoke "java.util.function.BiConsumer.accept(Object, Object)" because "this.messenger" is null` at `FileDeleter.getUnrefedFiles(FileDeleter.java:165)`.

## Files

- [src/org/apache/lucene/util/TestFileDeleterNullMessenger.java](src/org/apache/lucene/util/TestFileDeleterNullMessenger.java) (JUnit test)

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
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestFileDeleterNullMessenger
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFileDeleterNullMessenger.java](TestFileDeleterNullMessenger.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
