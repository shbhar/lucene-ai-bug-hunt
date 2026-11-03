# IndexWriterConfig.setIndexWriterEventListener accepts null, unlike the class's other component setters, so a later commit() that runs a merge-on-commit merge (on by default) throws NullPointerException instead of the setter rejecting the null (since 9.0.0; with the default config since 9.3.0)

Finding LFV-1188 · Area: core / index: IndexWriterConfig (setIndexWriterEventListener) and IndexWriter commit (merge-on-commit) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1188.html](../../bugs/LFV-1188.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You pass null to IndexWriterConfig.setIndexWriterEventListener, for example because your code forwards an optional listener that is not set. You expect what IndexWriterConfig does with every other null it does not support (since LUCENE-4833 its setters throw IllegalArgumentException, and setInfoStream points to its no-op sentinel), or else a writer that simply has no listener. Instead the setter stores the null, IndexWriter keeps it, and a later commit() that runs a merge-on-commit merge, which is on by default, throws NullPointerException 'because "this.eventListener" is null', far from the call that caused it.

## Expected vs actual

- Expected: The setter throws `IllegalArgumentException`, or `commit()` succeeds.
- Actual: `commit()` throws `NullPointerException: ... because "this.eventListener" is null` at `IndexWriter.prepareCommitInternal(IndexWriter.java:3890)`, assertions on and off.

## Files

- [src/org/apache/lucene/index/TestNullIndexWriterEventListener.java](src/org/apache/lucene/index/TestNullIndexWriterEventListener.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestNullIndexWriterEventListener
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestNullIndexWriterEventListener.java](TestNullIndexWriterEventListener.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
