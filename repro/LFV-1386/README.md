# HyphenationCompoundWordTokenFilterFactory passes the raw 'hyphenator' file name to the XML parser as the base system id (a URI), so with the JDK's built-in parser a valid hyphenation grammar named e.g. hyph_ø.xml, or with '%', '[', '{' or '\' in its name, fails to load with MalformedURIException

Finding LFV-1386 · Area: analysis-common / compound: hyphenation grammar loading (HyphenationCompoundWordTokenFilterFactory.inform; also HyphenationCompoundWordTokenFilter.getHyphenationTree(String) and PatternParser.parse(String)) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure HyphenationCompoundWordTokenFilterFactory with hyphenator=hyph_ø.xml, a valid FOP grammar with the standard DOCTYPE, and expect the analyzer to build, as it does when the same file is named hyph_o.xml. Instead, with the JDK's built-in XML parser, the build throws URI$MalformedURIException 'Path contains invalid character: ø': the factory hands the raw resource name to the parser as the base system id, which must be a URI, and the parser fails while resolving the DOCTYPE's relative 'hyphenation.dtd' against it, before Lucene's entity resolver gets the chance to supply the bundled DTD. The public getHyphenationTree(String) and PatternParser.parse(String), documented to take a file name, pass it as a URI the same way.

The test shows that a valid hyphenation grammar saved as `hyph_ø.xml` fails to load through the `hyphenationCompoundWord` factory, while the same file named `hyph_o.xml` loads.

## Expected vs actual

Expected: the analyzer builds. Actual, with the JDK's built-in XML parser (assertions on and off): `URI$MalformedURIException: Path contains invalid character: ø` from `CustomAnalyzer.Builder.addTokenFilter`. Run it without Apache Xerces on the class path (with Xerces 2.12.2 the test passes, so it cannot show the bug) and with a UTF-8 file-name encoding (under `LANG=C` the JVM cannot form the path and throws `InvalidPathException` instead).

## Files

- [src/org/apache/lucene/analysis/compound/TestHyphenatorNonAsciiName.java](src/org/apache/lucene/analysis/compound/TestHyphenatorNonAsciiName.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- Recorded outcome of run 1 on Lucene main 5adff78bb168: 1 test(s) run, 1 failed.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.compound.TestHyphenatorNonAsciiName
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
