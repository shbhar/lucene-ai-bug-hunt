# FieldType.equals and hashCode ignore the vector dimension, encoding and similarity function, so two field types that index vectors differently (or a vector type and a type without vectors) compare equal and hash alike (every release since 9.0.0)

Finding LFV-1255 · Area: core / document: FieldType (equals and hashCode) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1255.html](../../bugs/LFV-1255.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build two FieldTypes with setVectorAttributes, one 3-dimensional FLOAT32 with EUCLIDEAN similarity and one 768-dimensional BYTE with DOT_PRODUCT, and expect them to be different field types, since Lucene treats these settings as part of a field's schema (IndexWriter rejects a document whose vector dimension, encoding or similarity differs from the field's). Instead a.equals(b) is true and both have the same hashCode: FieldType.equals and hashCode compare 13 properties (point dimensions, doc values type, skip index, index options, norms, four term-vector flags, stored, tokenized) but none of the vector settings, although the copy constructor copies them. Earlier properties missing from these methods were treated as bugs and added (the point dimensions in LUCENE-7073, the doc-values skip index in GITHUB#13449); the vector settings, added in 9.0.0 and 9.4.0, never were.

## Expected vs actual

- Expected: `a` and `b` are not equal: they differ in dimension (3 vs 768), encoding and similarity.
- Actual: `AssertionError: Values should be different.`: `a.equals(b)` is true.

## Files

- [src/org/apache/lucene/document/TestFieldTypeVectorEquals.java](src/org/apache/lucene/document/TestFieldTypeVectorEquals.java) (JUnit test)

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
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestFieldTypeVectorEquals
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFieldTypeVectorEquals.java](TestFieldTypeVectorEquals.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
