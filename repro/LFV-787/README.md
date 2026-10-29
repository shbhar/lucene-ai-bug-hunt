# Mixing SuggestField and ContextSuggestField under one field name is accepted by addDocument and rejected only when the completion postings are written at flush or merge, where the IllegalArgumentException is tragic: the IndexWriter closes and loses every uncommitted document, and two cleanly committed segments of the two types make every merge that combines them close the writer again, also in a new writer (released since 5.3.0)

Bug LFV-787 · Area: suggest / document suggester (CompletionFieldsConsumer type check at flush and merge; core flush and merge abort paths) · Priority: low · Impact: index unusable · Novelty: new

Bug page: [../../bugs/LFV-787.html](../../bugs/LFV-787.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a SuggestField document and a ContextSuggestField document under the same field name; addDocument accepts both, and the suggest module's design (LUCENE-6459) says such a mix is rejected with an IllegalArgumentException, so you expect an input error that leaves the writer and your other documents alone. Instead the type is compared only in CompletionFieldsConsumer while postings are written, so the IllegalArgumentException 'single field name has mixed types' fires inside flush or merge, where IndexWriter treats every exception as tragic: the writer closes and drops every uncommitted document. If the two types landed in different segments, both commit cleanly, but every merge that combines them closes the writer, also in any new writer, until you delete all documents of one type.

The test shows that one SuggestField segment and one ContextSuggestField segment, both committed cleanly under the same field name; forceMerge(1) of the pair closes the IndexWriter.

## Expected vs actual

Expected: the writer stays open (rejecting the merge is fine); actual: tragic IllegalArgumentException 'single field name has mixed types', writer closed (-ea and -da).

## Files

- [src/org/apache/lucene/search/suggest/document/TestCompletionMixedTypesMerge.java](src/org/apache/lucene/search/suggest/document/TestCompletionMixedTypesMerge.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.document.TestCompletionMixedTypesMerge
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
