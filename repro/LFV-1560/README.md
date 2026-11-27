# (Latent) For a document that it reads from a sliced chunk, the compressing stored-fields reader of the default codec (not its merge instance) hands StoredFieldVisitor.binaryField an input that inherits DataInput's shallow clone(), so a visitor that reads through a clone and then reads the value from the input can get the value shifted, with no exception (run: one document with a 200,000-byte binary value and a short stored string, one byte read through the clone; in releases since 9.9.0); no code that clones this input was found in Lucene's own sources

Finding LFV-1560 · Area: core / codecs: stored fields (Lucene90CompressingStoredFieldsReader, the reader of the default codec's stored-fields format; the binary-field hook of StoredFieldVisitor; @lucene.experimental) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You store one document with a 200,000-byte binary value and a short string (default codec and IndexWriterConfig) and read it with your own StoredFieldVisitor that, inside binaryField, peeks at the first byte through a clone of the input it is given (in.clone().readByte()) and then reads the whole value from the input itself. You expect the stored bytes, since the javadoc of DataInput.clone() requires that clones 'may be positioned at different points in the input from each other and from the stream they were cloned from', and a 1,000-byte value does arrive intact. Instead the 200,000-byte value arrives shifted by one byte, with no exception (assertions on and off), because for a document in a 'sliced' chunk (a chunk whose documents hold at least 163,840 serialized bytes of stored fields with the default mode) the reader hands out a streaming input whose clone shares the buffered bytes and their read position with the original. It is latent for Lucene itself: no code that clones this input was found in Lucene's own sources, and the same document read without a clone, with a clone that is never read, or through the merge instance arrives as stored; what can be hit is an application visitor that clones the input and reads or skips through the clone (other shapes that were run end in an EOFException or in an IOException that reads like a damaged file), in each of the 23 releases from 9.9.0 to 10.6.0 (run with this one shape), on branch_11x and on main.

## Expected vs actual

- Expected: You expect the stored bytes, since the javadoc of DataInput.clone() requires that clones 'may be positioned at different points in the input from each other and from the stream they were cloned from', and a 1,000-byte value does arrive intact.
- Actual: The 200,000-byte value arrives shifted by one byte, with no exception (assertions on and off), because for a document in a 'sliced' chunk (a chunk whose documents hold at least 163,840 serialized bytes of stored fields with the default mode) the reader hands out a streaming input whose clone shares the buffered bytes and their read position with the original.

## Files

- [src/org/apache/lucene/index/TestStoredFieldDataInputClone.java](src/org/apache/lucene/index/TestStoredFieldDataInputClone.java) (JUnit test)

## Run it

- Tested on Lucene main 82cf7465bb8f (a commit of 2026-11-26): still reproduces.
- Tested on Lucene branch_11x 5d7ca54ba503 (a commit of 2026-11-19): reproduced.
- A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
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
java -da -dsa -Dtests.asserts=false -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestStoredFieldDataInputClone
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
