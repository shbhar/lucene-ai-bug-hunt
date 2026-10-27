# StandardQueryParser with setMultiFields normalizes field-less prefix and wildcard terms with field null, before the multi-field expansion, so per-field analyzers are ignored for them (and a PerFieldAnalyzerWrapper over Map.of throws NullPointerException)

Bug LFV-560 · Area: queryparser / flexible StandardQueryParser (processor pipeline order: WildcardQueryNodeProcessor runs before MultiFieldQueryNodeProcessor) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-560.html](../../bugs/LFV-560.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give StandardQueryParser a PerFieldAnalyzerWrapper (WhitespaceAnalyzer by default, StandardAnalyzer for 'title'), call setMultiFields({title}) and parse 'Luc*' with no field, expecting what the javadoc promises: the clause expanded over the multi fields, i.e. the same query as 'title:Luc*', which becomes title:luc* and finds the document titled 'Lucene'. Instead the pipeline normalizes the prefix in WildcardQueryNodeProcessor before MultiFieldQueryNodeProcessor has set the field, so Analyzer#normalize is called with field null (the wrapper's default analyzer), the query becomes title:Luc* and finds nothing, while plain, fuzzy, regexp and range clauses in the same setup are normalized with the expanded field. If the per-field map is a Map.of, the null lookup makes parse throw NullPointerException for every field-less prefix or wildcard clause.

## Expected vs actual

- Expected: `Luc*` and `Lu?ene` find the title "Lucene" (`title:luc*`), `Jo*` and `J?hn` find "John", and with the `Map.of` map `Luc*` parses to `title:luc*`.
- Actual: `title:Luc*` and `firstname:jo* lastname:jo*` with 0 hits, and `NullPointerException` from `parse` with the `Map.of` map (main, 10.5.2, 10.0.0, 9.12.3).

## Files

- [src/org/apache/lucene/queryparser/flexible/standard/TestMultiFieldWildcardNormalize.java](src/org/apache/lucene/queryparser/flexible/standard/TestMultiFieldWildcardNormalize.java) (JUnit test)

## Run it

- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queryparser.flexible.standard.TestMultiFieldWildcardNormalize
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestMultiFieldWildcardNormalization.java](TestMultiFieldWildcardNormalization.java)
- [TestMultiFieldWildcardPerFieldAnalyzerWrapper.java](TestMultiFieldWildcardPerFieldAnalyzerWrapper.java)
- [fix-with-test.diff](fix-with-test.diff)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
