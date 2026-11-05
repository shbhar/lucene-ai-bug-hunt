# SynonymGraphFilterFactory and the deprecated SynonymFilterFactory do not trim the names in a comma-separated 'synonyms' file list, so synonyms="a.txt, b.txt" fails to build with IOException 'Resource not found ...: b.txt', although every other list-taking analysis factory accepts that form

Finding LFV-1389 · Area: analysis-common / synonym: synonym file loading (SynonymGraphFilterFactory.loadSynonyms; also the deprecated SynonymFilterFactory) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an analyzer with the synonymGraph filter and synonyms="a.txt, b.txt" (two synonym files, a blank after the comma) and expect both files to load, as the same list form does for the stop, keep-word, type and mapping factories. Instead the build throws IOException 'Resource not found ...: b.txt': the factory splits the list but asks the resource loader for ' b.txt' with its leading blank, while every other caller of the shared file-list splitter trims each name. The same list without the blank works, and the deprecated SynonymFilterFactory fails the same way.

## Expected vs actual

- Expected: Both files load and `foo qux` analyzes to `[baz, quux]`.
- Actual: `java.io.IOException: Resource not found (...): b.txt` from `SynonymGraphFilterFactory.loadSynonyms(SynonymGraphFilterFactory.java:191)`; the analyzer is never built.

## Files

- [src/org/apache/lucene/analysis/synonym/TestSynonymFileListTrim.java](src/org/apache/lucene/analysis/synonym/TestSynonymFileListTrim.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.synonym.TestSynonymFileListTrim
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
