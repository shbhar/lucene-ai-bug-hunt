# Lucene90StoredFieldsFormat's class javadoc says the BEST_SPEED / BEST_COMPRESSION stored-fields modes 'can be configured like this' with indexWriterConfig.setCodec(new Lucene100Codec(Mode...)), but since 10.1.0 Lucene100Codec is a read-only codec in lucene-backward-codecs: the example does not compile against lucene-core, and with backward-codecs on the classpath the first commit of a document with an indexed field throws UnsupportedOperationException and closes the IndexWriter (documentation defect)

Finding LFV-1446 · Area: core / codecs (Lucene90StoredFieldsFormat class javadoc configuration example) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1446.html](../../bugs/LFV-1446.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

The Lucene90StoredFieldsFormat javadoc says you can switch stored fields to the smaller BEST_COMPRESSION mode 'like this': indexWriterConfig.setCodec(new Lucene100Codec(Mode.BEST_COMPRESSION)). Since Lucene 10.1.0, Lucene100Codec is no longer the current codec but a read-only one in lucene-backward-codecs, so the example does not compile against lucene-core, and with backward-codecs on the classpath the first commit of a document with an indexed field throws UnsupportedOperationException ('This postings format may not be used for writing, use the current postings format') and closes the IndexWriter. The same steps with the current codec, new Lucene104Codec(Lucene104Codec.Mode.BEST_COMPRESSION), work, so only the javadoc is wrong.

## Expected vs actual

- Expected: The commit succeeds and the reader sees 1 document.
- Actual: `commit()` throws `UnsupportedOperationException: This postings format may not be used for writing, use the current postings format`, with assertions on and off, on main and on 10.6.0.

## Files

- [src/org/apache/lucene/codecs/lucene90/TestStoredFieldsFormatJavadocExample.java](src/org/apache/lucene/codecs/lucene90/TestStoredFieldsFormatJavadocExample.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/backward-codecs/build/libs/lucene-backward-codecs-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.lucene90.TestStoredFieldsFormatJavadocExample
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestStoredFieldsFormatJavadocExample.java](TestStoredFieldsFormatJavadocExample.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
