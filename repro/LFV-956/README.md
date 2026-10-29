# SynonymGraphFilterFactory and SynonymFilterFactory keep a UTF-8 byte order mark at the start of a synonyms file, so the file's first rule silently stops working and a commented-out first line that contains a comma becomes a live rule

Bug LFV-956 · Area: analysis-common / synonym file loading (SynonymGraphFilterFactory and the deprecated SynonymFilterFactory loadSynonyms -> SolrSynonymParser, WordnetSynonymParser) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-956.html](../../bugs/LFV-956.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure SynonymGraphFilterFactory (or SynonymFilterFactory) with a synonyms file that an editor saved as UTF-8 with a byte order mark (bytes EF BB BF) and expect the same rules as from the file without the mark, because Lucene's shared resource-line loader skips a leading BOM for the other file-based analysis factories (WordlistLoader.getLines, 'skip initial bom marker', SOLR-1865) and the synonym factory itself skipped it until 3.4.0. Instead the factory decodes the file with its own UTF-8 reader and SolrSynonymParser keeps U+FEFF glued to the first line: with the first rule 'foo, bar' the text 'foo' is no longer expanded (and 'bar' gains the invisible synonym '\uFEFFfoo'), a first rule 'baz =&gt; qux' never fires, and a first line '#couch, sofa' is no longer a comment, so 'sofa' expands to '\uFEFF#couch'. In WordNet format the fixed-column synset id (substring(2, 11)) is shifted by one character, so the first word ends up in a synset of its own; nothing reports an error.

## Expected vs actual

- Expected: Each first line behaves as in the file without the mark: `foo` -&gt; `[bar, foo]`, `baz` -&gt; `[qux]`, `sofa` stays `[sofa]`.
- Actual: 5 of 6 tests fail, assertions on and off: `foo` -&gt; `[foo]`, `baz` -&gt; `[baz]`, `sofa` -&gt; `[\uFEFF#couch, sofa]`; the stop-filter control passes.

## Files

- [src/org/apache/lucene/analysis/synonym/TestSynonymFileUtf8Bom.java](src/org/apache/lucene/analysis/synonym/TestSynonymFileUtf8Bom.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.synonym.TestSynonymFileUtf8Bom
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestSynonymFileUtf8Bom.java](TestSynonymFileUtf8Bom.java)
- [fix-option-F-factories.diff](fix-option-F-factories.diff)
- [TestSynonymBomOnDisk.java](TestSynonymBomOnDisk.java)
- [TestSynonymsFileBomV1.java](TestSynonymsFileBomV1.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
