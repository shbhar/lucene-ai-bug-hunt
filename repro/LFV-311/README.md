# HTMLStripCharFilter silently drops all text after a &lt;!-- --&gt;-wrapped &lt;script&gt; or &lt;style&gt; block whose content has an unbalanced quote (for example the apostrophe in a JS comment '// Don't remove' or a CSS comment '/* client's fix */'): the quote opens a quoted-string state that ignores '--&gt;' and the end tag, and at end of input everything after the quote is removed, including the text after the closed &lt;/script&gt; or &lt;/style&gt;

Bug LFV-311 · Area: analysis / HTMLStripCharFilter (JFlex grammar: SCRIPT_COMMENT, STYLE_COMMENT, SINGLE_/DOUBLE_QUOTED_STRING and the %eof exclude list; also the 'htmlStrip' char filter SPI) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-311.html](../../bugs/LFV-311.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You strip '&lt;script&gt;&lt;!--\n// Don't remove\nvar x = 1;\n//--&gt;&lt;/script&gt;&lt;p&gt;Welcome to our store&lt;/p&gt;' with HTMLStripCharFilter and expect 'Welcome to our store', which is what you get without the apostrophe and what the filter's own tests expect after wrapped script and style blocks. Instead the output is empty: inside the &lt;!-- --&gt; section the apostrophe opens a quoted-string state that ignores '--&gt;' and '&lt;/script&gt;', and because the input ends in that state everything after the quote is dropped, so the text is never indexed and a TermQuery for a word after the block misses the document. The same happens with an apostrophe in a CSS comment of a wrapped &lt;style&gt; block or with a lone '"', with no error.

The test shows that an apostrophe in a JavaScript comment inside a `<!-- -->`-wrapped `<script>` block makes HTMLStripCharFilter drop all text after the block.

## Expected vs actual

Expected the output of `<script><!--\n// Don't remove\n//--></script><p>Welcome to our store</p>` to contain `Welcome to our store`; actual output: the empty string, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/charfilter/TestHTMLStripQuoteInScriptComment.java](src/org/apache/lucene/analysis/charfilter/TestHTMLStripQuoteInScriptComment.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.charfilter.TestHTMLStripQuoteInScriptComment
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
