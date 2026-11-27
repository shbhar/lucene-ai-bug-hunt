# FieldType's javadoc still describes null as 'no doc values': setDocValuesType documents '@param type DocValues type, or null if no DocValues should be stored' but null throws NullPointerException, and docValuesType() documents 'The default is null' but returns DocValuesType.NONE (documentation defect, stale since 5.0.0; the code is intended and must not change)

Finding LFV-1498 · Area: document / FieldType (javadoc of setDocValuesType and docValuesType(); the null refusal and the NONE default beside them) · Priority: low · Impact: minor · Novelty: new

Finding page: [https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1498.html](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1498.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build your own FieldType and call setDocValuesType(null) to switch doc values off, because its javadoc says '@param type DocValues type, or null if no DocValues should be stored', or you test docValuesType() == null because its javadoc says 'The default is null (no docValues)'. You expect a field type without doc values and a null default. Instead the setter throws NullPointerException 'DocValuesType must not be null' and the getter returns DocValuesType.NONE, so the null test is false for every FieldType made with new FieldType() and the setter. Lucene 5.0 replaced null by DocValuesType.NONE on purpose (LUCENE-6039) and left these two sentences, so only the javadoc is wrong (pass DocValuesType.NONE); the stale default sentence of indexOptions() beside them was pointed out by A. Coady on Stack Overflow in 2018 and is not part of this finding.

## Expected vs actual

- Expected: A field type without doc values: the `@param` sentence says null means "no DocValues should be stored".
- Actual: `NullPointerException: DocValuesType must not be null` at `FieldType.setDocValuesType(FieldType.java:515)`, with assertions on and off.

## Files

- [src/org/apache/lucene/document/TestFieldTypeSetDocValuesTypeNull.java](src/org/apache/lucene/document/TestFieldTypeSetDocValuesTypeNull.java) (JUnit test)

## Run it

- Tested on Lucene main 82cf7465bb8f (a commit of 2026-11-26): still reproduces.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced.
- What the test does: it makes the one call new FieldType().setDocValuesType(null), which the setter's javadoc offers for 'no DocValues should be stored'. The test fails because the call throws NullPointerException 'DocValuesType must not be null', the same with and without assertions; it uses no randomness. The finding is about the javadoc: refusing null is intended, and DocValuesType.NONE is the value to pass. The test does not exercise the getter's sentence ('The default is null'); the field's initial value in the source is DocValuesType.NONE.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestFieldTypeSetDocValuesTypeNull
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Files shown on the finding page

- [TestFieldTypeSetDocValuesTypeNull.java](TestFieldTypeSetDocValuesTypeNull.java)

Formal models and scripts this finding's part of the hunt used: [../../models/documented-legal-values-vs-code/](../../models/documented-legal-values-vs-code/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
