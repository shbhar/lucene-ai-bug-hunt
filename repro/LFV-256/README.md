# PerFieldDocValuesFormat and PerFieldKnnVectorsFormat accept a per-field hook that returns another per-field format (for example the default codec's docValuesFormat or knnVectorsFormat); the nested writer overwrites the outer per-field attributes, so the committed segment cannot be opened, while PerFieldPostingsFormat rejects the same configuration

Bug LFV-256 · Area: codecs / per-field doc values and vectors formats (PerFieldDocValuesFormat, PerFieldKnnVectorsFormat attribute bookkeeping) · Priority: medium · Impact: index unusable · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You write a codec whose per-field doc-values (or vectors) hook returns another per-field format, for example the default codec's docValuesFormat, add one document and close the writer. You expect the writer to reject the configuration, as PerFieldPostingsFormat does ('cannot embed PerFieldPostingsFormat inside itself'), or the committed index to open. Instead the commit succeeds, because the nested writer silently overwrote the outer per-field attributes with its own format name and suffix, and DirectoryReader.open fails with 'No sub-file with id _Lucene90_0.dvm' (vectors: _Lucene99HnswVectorsFormat_0.vemf) since the files were written as _0_PerFieldDV40_0_Lucene90_0.*.

## Expected vs actual

- Expected: You expect the writer to reject the configuration, as PerFieldPostingsFormat does ('cannot embed PerFieldPostingsFormat inside itself'), or the committed index to open.
- Actual: The commit succeeds, because the nested writer silently overwrote the outer per-field attributes with its own format name and suffix, and DirectoryReader.open fails with 'No sub-file with id _Lucene90_0.dvm' (vectors: _Lucene99HnswVectorsFormat_0.vemf) since the files were written as _0_PerFieldDV40_0_Lucene90_0.*.

## Files

- [src/org/apache/lucene/codecs/perfield/TestPerFieldNestedFormat.java](src/org/apache/lucene/codecs/perfield/TestPerFieldNestedFormat.java) (JUnit test)
- [src2/org/apache/lucene/codecs/perfield/TestPerFieldNestedFormat.java](src2/org/apache/lucene/codecs/perfield/TestPerFieldNestedFormat.java) (JUnit test)

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

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.perfield.TestPerFieldNestedFormat
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.perfield.TestPerFieldNestedFormat
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
