# KnnFloatVectorField(String, float[], FieldType) accepts an empty vector when the FieldType has no vector attributes (vector dimension 0, as new FieldType() gives), although its javadoc promises IllegalArgumentException for an empty vector; if the document is indexed, the field is registered as a field without vectors (the KnnByteVectorField twin has the same gap; every release since 9.0.0)

Finding LFV-1241 · Area: core / document: KnnFloatVectorField and KnnByteVectorField FieldType constructors (argument validation) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1241.html](../../bugs/LFV-1241.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build new KnnFloatVectorField("v", new float[0], new FieldType()) and expect the IllegalArgumentException that this constructor's javadoc promises when 'the vector is empty' (the convenience constructors throw 'cannot index an empty vector' for the same vector). Instead the field is built without error: the constructor's only length check compares vector.length with fieldType.vectorDimension(), and a FieldType without vector attributes has dimension 0, so 0 == 0 passes. If the document is indexed, v is registered as a field with no vectors (not indexed, no doc values, not stored), and later real vectors for v in that index are refused by the per-field schema check.

## Expected vs actual

- Expected: `IllegalArgumentException`, as the javadoc promises for an empty vector and as the convenience constructors throw ("cannot index an empty vector").
- Actual: `AssertionFailedError: Expected exception IllegalArgumentException but no exception was thrown`: the field is built.

## Files

- [src/org/apache/lucene/document/TestKnnFloatVectorFieldEmptyVector.java](src/org/apache/lucene/document/TestKnnFloatVectorFieldEmptyVector.java) (JUnit test)

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
     execute --select-class org.apache.lucene.document.TestKnnFloatVectorFieldEmptyVector
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestKnnFloatVectorFieldEmptyVector.java](TestKnnFloatVectorFieldEmptyVector.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
