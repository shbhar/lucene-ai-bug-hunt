# XML query parser ignores the text inside CDATA sections: DOMUtils.getText has no CDATA_SECTION_NODE case, so &lt;TermsQuery fieldName="f"&gt;a &lt;![CDATA[b]]&gt;&lt;/TermsQuery&gt; parses to f:a only and a CDATA-only LikeThisQuery silently matches nothing (every release since 1.9)

Finding LFV-1383 · Area: queryparser / xml (DOMUtils.getText, the text reader of every text-taking builder; CoreParser) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1383.html](../../bugs/LFV-1383.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse an XML query whose text is partly or wholly a CDATA section, for example &lt;TermsQuery fieldName="f"&gt;a &lt;![CDATA[b]]&gt;&lt;/TermsQuery&gt;, with Lucene's XML query parser. XML defines a CDATA section as ordinary character data, and the shipped DTD declares TermsQuery as (#PCDATA) whose 'Child text is analyzed', so you expect the same query as for the plain text 'a b', (f:a f:b). Instead DOMUtils.getText skips CDATA section nodes and you get (f:a): mixed text silently loses the CDATA words, a CDATA-only LikeThisQuery matches nothing, and the other CDATA-only elements throw ParserException.

## Expected vs actual

- Expected: Both inputs parse to `(f:a f:b)^1.0`.
- Actual: `expected:<(f:a f:b)^1.0> but was:<(f:a)^1.0>`: the CDATA text `b` is dropped.

## Files

- [src/org/apache/lucene/queryparser/xml/TestCoreParserCdataText.java](src/org/apache/lucene/queryparser/xml/TestCoreParserCdataText.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queryparser.xml.TestCoreParserCdataText
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestCoreParserCdataText.java](TestCoreParserCdataText.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
