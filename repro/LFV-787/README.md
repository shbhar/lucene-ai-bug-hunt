# Mixing SuggestField and ContextSuggestField under one field name is accepted by addDocument and rejected only when the completion postings are written at flush or merge, where the IllegalArgumentException is tragic: the IndexWriter closes and loses every uncommitted document, and two cleanly committed segments of the two types make every merge that combines them close the writer again, also in a new writer (released since 5.3.0)

Bug LFV-787 · Area: suggest / document suggester (CompletionFieldsConsumer type check at flush and merge; core flush and merge abort paths) · Priority: medium · Impact: index unusable · Novelty: new

Bug page: [../../bugs/LFV-787.html](../../bugs/LFV-787.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a SuggestField document and a ContextSuggestField document under the same field name; addDocument accepts both, and the suggest module's design (LUCENE-6459) says such a mix is rejected with an IllegalArgumentException, so you expect an input error that leaves the writer and your other documents alone. Instead the type is compared only in CompletionFieldsConsumer while postings are written, so the IllegalArgumentException 'single field name has mixed types' fires inside flush or merge, where IndexWriter treats every exception as tragic: the writer closes and drops every uncommitted document. If the two types landed in different segments, both commit cleanly, but every merge that combines them closes the writer, also in any new writer, until you delete all documents of one type.

## Expected vs actual

- Expected: `forceMerge(1)` of the two committed segments leaves the writer open (or rejects the merge), and documents 1, 2 and 3 stay in the index.
- Actual: `IllegalArgumentException: single field name has mixed types` closes the `IndexWriter` and document 3 is lost; 6 of 8 tests fail this way, at commit, forceMerge, a natural merge and in a new writer.

## Files

- [src/org/apache/lucene/search/suggest/document/TestCompletionMixedTypesClosesWriter.java](src/org/apache/lucene/search/suggest/document/TestCompletionMixedTypesClosesWriter.java) (JUnit test)
- [src/org/apache/lucene/search/suggest/document/TestCompletionMixedTypesClosesWriterXver.java](src/org/apache/lucene/search/suggest/document/TestCompletionMixedTypesClosesWriterXver.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.document.TestCompletionMixedTypesClosesWriter

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.document.TestCompletionMixedTypesClosesWriterXver
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestMixedSuggestFieldTypes.java](TestMixedSuggestFieldTypes.java)
- [TestCompletionMixedTypesClosesWriter.java](TestCompletionMixedTypesClosesWriter.java)
- [TestCompletionMixedTypes.java](TestCompletionMixedTypes.java)
- [CompletionMixedTypesMain.java](CompletionMixedTypesMain.java)
- [TestMixedMarkerFixProbe.java](TestMixedMarkerFixProbe.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
