# ContextSuggestField accepts contexts that contain U+001E or U+001F although its javadoc promises an IllegalArgumentException for reserved characters; the completion analysis then drops U+001E (a suggestion indexed under context 'a&lt;U+001E&gt;b' is stored as, returned for and labelled with the exact context 'ab') and doubles U+001F (context 'a&lt;U+001F&gt;b' never matches its own exact query and is reported as 'a&lt;U+001F&gt;&lt;U+001F&gt;b')

Bug LFV-522 · Area: suggest / document (NRT) suggester (ContextSuggestField context validation; the context then passes through ConcatenateGraphFilter's SEP_LABEL escaping and HOLE removal) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-522.html](../../bugs/LFV-522.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index one suggestion under context 'ab' and another under context 'a&lt;U+001E&gt;b' with ContextSuggestField, and expect an IllegalArgumentException, because the constructor javadoc says it throws one 'if value or contexts contains any reserved characters' and U+001E and U+001F are exactly the characters that SuggestField and CompletionQuery reject as reserved (or, at the least, a context that round-trips). Instead both documents are indexed: ContextQuery.addContext("ab") returns both and reports both with context 'ab', addContext("a&lt;U+001E&gt;b") returns nothing, and a context 'a&lt;U+001F&gt;b' is also accepted, its exact query returns nothing and addAllContexts reports it as 'a&lt;U+001F&gt;&lt;U+001F&gt;b'. The cause: ContextSuggestField.validate checks a context only for U+001D (CONTEXT_SEPARATOR); the context then goes through ConcatenateGraphFilter, which turns a U+001E byte into an epsilon and escapes U+001F by doubling it, while ContextQuery builds its automaton from the raw characters, so the index side and the query side disagree.

The test shows that a `ContextSuggestField` context containing U+001E is accepted and merged into another context: a suggestion indexed under `a<U+001E>b` is returned, labelled `ab`, by an exact `ContextQuery.addContext("ab")`.

## Expected vs actual

Expected `addDocument` to reject the reserved character (the constructor's @throws) or the exact `ab` query to return only `[s0@ab]`; actual `[s0@ab, s1@ab]`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/suggest/document/TestContextSuggestFieldHoleCharContext.java](src/org/apache/lucene/search/suggest/document/TestContextSuggestFieldHoleCharContext.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.suggest.document.TestContextSuggestFieldHoleCharContext
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
