# MemoryIndex takes omitNorms from the first instance of a field name, so a doc-values or stored-only instance added first gives an omitNorms text or keyword field norms and different scores than IndexWriter

Bug LFV-342 · Area: memory / MemoryIndex field schema (FieldInfo built once from the first IndexableField of a name: omitNorms and norms); reached in-tree through monitor single-document batches · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You put a document into a MemoryIndex in which a SortedDocValuesField (or a NumericDocValuesField or a StoredField) named title comes before Field("title", "quick brown fox") with a type that omits norms, and expect what IndexWriter gives for the same document: no norms for title and the score of a field without norms. Instead MemoryIndex builds the field's FieldInfo from the first instance, whose type reports omitNorms=false, so title gets norms and TermQuery title:fox scores 0.13076457 instead of IndexWriter's 0.1798013; with the text field first, MemoryIndex agrees with IndexWriter. The same happens for a multi-valued StringField with a SortedSetDocValuesField before each value, and in the mirror order (a non-indexed type with omitNorms=true before a text field with norms) MemoryIndex drops the norms instead.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: MemoryIndex builds the field's FieldInfo from the first instance, whose type reports omitNorms=false, so title gets norms and TermQuery title:fox scores 0.13076457 instead of IndexWriter's 0.1798013; with the text field first, MemoryIndex agrees with IndexWriter.

## Files

- [src/org/apache/lucene/index/memory/TestMemoryIndexOmitNormsFieldOrder.java](src/org/apache/lucene/index/memory/TestMemoryIndexOmitNormsFieldOrder.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.memory.TestMemoryIndexOmitNormsFieldOrder
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
