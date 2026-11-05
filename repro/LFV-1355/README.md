# IndexWriterConfig.setRAMBufferSizeMB (also live on an open writer's config) accepts NaN although it documents an IllegalArgumentException for an enabled RAM buffer that is not &gt; 0.0, so the writer silently flushes one segment per document (the same check has admitted NaN since 2.3.0)

Finding LFV-1355 · Area: index / IndexWriter configuration (RAM buffer setter) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1355.html](../../bugs/LFV-1355.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You set the IndexWriter's RAM buffer with setRAMBufferSizeMB and the value is NaN, for example from a 0/0 in a sizing formula or a configuration string 'NaN'. You expect the IllegalArgumentException that the javadoc promises for an enabled buffer that is not positive ('ramBufferSize should be &gt; 0.0 MB when enabled'), which 0.0, -0.0 and -Infinity do get. Instead the check 'ramBufferSizeMB &lt;= 0.0' is false for NaN, so NaN is stored: the default flush policy then computes a 0-byte limit and writes a new segment for every added document, and IndexWriter never writes buffered doc-values updates back early; the indexed data stays correct.

## Expected vs actual

- Expected: `setRAMBufferSizeMB(Double.NaN)` throws `IllegalArgumentException: ramBufferSize should be > 0.0 MB when enabled`.
- Actual: `AssertionFailedError: Expected exception IllegalArgumentException but no exception was thrown`; NaN is stored.

## Files

- [src/org/apache/lucene/index/TestRamBufferSizeNaN.java](src/org/apache/lucene/index/TestRamBufferSizeNaN.java) (JUnit test)

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
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestRamBufferSizeNaN
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestRamBufferSizeNaN.java](TestRamBufferSizeNaN.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
