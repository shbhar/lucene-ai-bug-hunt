# HTMLStripCharFilter drops an unclosed '&lt;name' or '&lt;/name' when the input ends right after the name, but outputs the same text when any character follows it, so 'a &lt; b' indexes only 'a'

Bug LFV-878 · Area: analysis / HTMLStripCharFilter (JFlex grammar: the %eof end-of-input switch for the start- and end-tag tail states; also the 'htmlStrip' char filter SPI) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-878.html](../../bugs/LFV-878.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run 'price a &lt; b' through HTMLStripCharFilter and StandardTokenizer and expect [price, a, b], which is what you get for 'price a &lt; b ' (one trailing space) or 'price a &lt; b c'. Instead you get [price, a]: when the input ends right after '&lt;' [whitespace] Name or '&lt;/' Name with no '&gt;', the end-of-input handler throws the pending text away, while anywhere else the same unclosed construct is output as text, as the upstream tests require for '&lt;ahref=http:..' at end of input. A TermQuery for 'b' therefore misses the document; other shapes are 'a&lt;b' -&gt; 'a', 'x &lt;p' -&gt; 'x ', 'a &lt;/b' -&gt; 'a ' and 'if a &lt; b.' -&gt; 'if a ' ('.' is a name character).

`HTMLStripCharFilter` drops an unclosed `< name` when the input ends right after the name, but outputs it as text when any character follows.

## Expected vs actual

Expected: `a < b` -&gt; `a < b` (as `a < b ` -&gt; `a < b `); actual: `a ` (`expected:<a [< b]> but was:<a []>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/charfilter/TestHTMLStripUnclosedTagAtEnd.java](src/org/apache/lucene/analysis/charfilter/TestHTMLStripUnclosedTagAtEnd.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.charfilter.TestHTMLStripUnclosedTagAtEnd
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/htmlstrip-tag-open-name-class-vs-html-tokenizer-reference/](../../models/htmlstrip-tag-open-name-class-vs-html-tokenizer-reference/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
