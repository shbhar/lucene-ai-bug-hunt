# FileDictionary(InputStream) and PlainTextDictionary(Path/InputStream) decode the file as UTF-8 themselves but keep a leading byte order mark, so the file's first entry carries an invisible U+FEFF: prefix suggesters never suggest it and SpellChecker.exist does not find it

Bug LFV-990 · Area: suggest / text-file dictionaries (FileDictionary feeding Lookup.build, PlainTextDictionary feeding SpellChecker.indexDictionary) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a suggester or a spell checker from a UTF-8 text file whose first line is 'foo&lt;TAB&gt;5' (or just 'foo') but which an editor saved with a byte order mark (the bytes EF BB BF), passing it to FileDictionary(InputStream) or PlainTextDictionary(Path or InputStream), whose javadoc says 'NOTE: content is treated as UTF-8', and you expect the same entries as from the same file without the mark, because the mark is only an encoding signature and Lucene's own line loaders skip it (WordlistLoader.getLines, SOLR-1865; hunspell, LUCENE-5505). Instead these constructors decode the bytes with Java's UTF-8 decoder, which returns the mark as the character U+FEFF, and nothing removes it, so the first entry becomes '\uFEFFfoo': WFSTCompletionLookup, FSTCompletionLookup and TSTLookup return nothing for lookup("fo"), AnalyzingSuggester returns '\uFEFFfoo', and a SpellChecker reports exist("foo") = false. No error is raised and only the first line is affected.

The test shows that `FileDictionary(InputStream)`, which decodes the stream as UTF-8 itself, keeps a leading UTF-8 byte order mark in the file's first entry.

## Expected vs actual

Expected the first entry of EF BB BF `foo<TAB>5` to be `foo` (`[66 6f 6f]`); actual: `\uFEFFfoo` (`[ef bb bf 66 6f 6f]`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/suggest/TestFileDictionaryUtf8Bom.java](src/org/apache/lucene/search/suggest/TestFileDictionaryUtf8Bom.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.suggest.TestFileDictionaryUtf8Bom
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
