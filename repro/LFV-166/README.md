# UniformSplit postings formats fail to flush or merge a field when deltaNumLines &gt; targetNumBlockLines/3, a setting validateSettings accepts

Bug LFV-166 · Area: codecs / UniformSplit postings formats (BlockWriter last-block split; UniformSplit and SharedTermsUniformSplit) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a UniformSplit (or SharedTermsUniformSplit) postings format with targetNumBlockLines=4 and deltaNumLines=2, which the constructor accepts because its documented rule is only 0 &lt;= delta &lt; target, and commit a field with 3 terms. You expect the commit to succeed and the blocks to hold target +- delta lines. Instead the flush throws (AssertionError 'blockLines size: 3' with assertions on, IndexOutOfBoundsException 'Index -1 out of bounds for length 3' with them off) and the IndexWriter closes, because the code that splits a field's last block assumes its look-back window of 2*delta lines starts at line 1 or later, which is false whenever 3*delta &gt; target and the last block has between target-delta+1 and 2*delta lines.

## Expected vs actual

- Expected: You expect the commit to succeed and the blocks to hold target +- delta lines.
- Actual: The flush throws (AssertionError 'blockLines size: 3' with assertions on, IndexOutOfBoundsException 'Index -1 out of bounds for length 3' with them off) and the IndexWriter closes, because the code that splits a field's last block assumes its look-back window of 2*delta lines starts at line 1 or later, which is false whenever 3*delta &gt; target and the last block has between target-delta+1 and 2*delta lines.

## Files

- [src/org/apache/lucene/codecs/uniformsplit/TestUniformSplitValidSettingsFlush.java](src/org/apache/lucene/codecs/uniformsplit/TestUniformSplitValidSettingsFlush.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.uniformsplit.TestUniformSplitValidSettingsFlush
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
