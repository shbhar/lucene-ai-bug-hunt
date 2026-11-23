# Latent: the DocIdSetIterator that UnifiedHighlighter passes to its protected loadFieldValues answers NO_MORE_DOCS from docID() while it is fresh, before its first move, where DocIdSetIterator.docID() documents -1; no returned snippet was wrong in what was run

Finding LFV-1567 · Area: highlighter / UnifiedHighlighter (the loadFieldValues extension point: the document cursor made by asDocIdSetIterator) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You subclass UnifiedHighlighter and override its protected loadFieldValues, which its javadoc names as the place to change where the text to highlight is loaded from, and in the first loader call of a highlight call you read docID() of the DocIdSetIterator you are handed before anything has moved it; you expect -1, which DocIdSetIterator.docID() documents for an iterator on which neither nextDoc() nor advance() was called yet. Instead it answers 2147483647 (NO_MORE_DOCS, documented as 'the iterator has exhausted') while its documents are still to come, with assertions on and off (run with one document 'a test' and one TermQuery on branch_11x 75eea467ac52, main f2b061406c4b and the 10.6.0 release; the snippet 'a &lt;b&gt;test&lt;/b&gt;' is right): by the source, the cursor's docID() has one answer for 'before the first document' and for 'after the last'. It is latent: Lucene's own loader moves the cursor with nextDoc() and never reads its docID(), no returned snippet was wrong in anything that was run, and in what was read (Lucene's loader, the three overrides in its tests, nine public override files) no loader reads the fresh cursor's docID(); it is not new in 11.0: the 10.6.0 release fails the same test, and the value was seen on each of the 88 releases from 6.3.0, the first with the class, to 10.6.0 (the test on 33 of them, the same check as a small program on the 55 older ones).

## Expected vs actual

- Expected: You expect -1, which DocIdSetIterator.docID() documents for an iterator on which neither nextDoc() nor advance() was called yet.
- Actual: It answers 2147483647 (NO_MORE_DOCS, documented as 'the iterator has exhausted') while its documents are still to come, with assertions on and off (run with one document 'a test' and one TermQuery on branch_11x 75eea467ac52, main f2b061406c4b and the 10.6.0 release; the snippet 'a &lt;b&gt;test&lt;/b&gt;' is right): by the source, the cursor's docID() has one answer for 'before the first document' and for 'after the last'.

## Files

- [src/org/apache/lucene/search/uhighlight/TestUnifiedHighlighterLoadFieldValuesCursor.java](src/org/apache/lucene/search/uhighlight/TestUnifiedHighlighterLoadFieldValuesCursor.java) (JUnit test)

## Run it

- Tested on Lucene main 11c8b6ea99ae (a commit of 2026-11-23): still reproduces.
- Tested on Lucene main f2b061406c4b (a commit of 2026-11-19): reproduced.
- A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed C0FFEE910
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=C0FFEE910 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.uhighlight.TestUnifiedHighlighterLoadFieldValuesCursor
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
