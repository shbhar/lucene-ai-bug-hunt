# Field.storedValue() handles a String but no other CharSequence, so a stored field built from a StringBuilder, CharsRef or other CharSequence through the public StoredField(String, CharSequence, FieldType) or Field(String, CharSequence, IndexableFieldType) constructor makes IndexWriter.addDocument throw IllegalStateException 'Cannot store value of type ...' (regression in 9.6.0 from #12116)

Finding LFV-1170 · Area: document / stored values (Field.storedValue, StoredField) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1170.html](../../bugs/LFV-1170.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add a document holding new StoredField("f", new StringBuilder("x"), StoredField.TYPE) to an IndexWriter; you expect the value to be stored and read back as "x", since the public constructor takes a CharSequence and Lucene 7.7.0 to 9.5.x did exactly that. Instead addDocument throws IllegalStateException 'Cannot store value of type class java.lang.StringBuilder', because Field.storedValue(), added in 9.6.0 by #12116, has a text branch for String only and sends every other CharSequence to its error branch. The same happens for a Field with a stored type such as TextField.TYPE_STORED over a StringBuilder or CharsRef; only that document is rejected, and value.toString() works around it.

## Expected vs actual

- Expected: The document is added and its stored field reads back as `"x"`, as on 9.5.0.
- Actual: `addDocument` throws `IllegalStateException: Cannot store value of type class java.lang.StringBuilder` at `Field.storedValue(Field.java:622)`, with assertions on and off.

## Files

- [src/org/apache/lucene/document/TestStoredFieldCharSequence.java](src/org/apache/lucene/document/TestStoredFieldCharSequence.java) (JUnit test)

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

# run 1: assertions on, seed 1D0A640B9F1714F5
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=1D0A640B9F1714F5 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestStoredFieldCharSequence
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestStoredFieldCharSequence.java](TestStoredFieldCharSequence.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
