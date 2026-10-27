# WikipediaTokenizer silently drops Chinese and Japanese text inside wiki markup (links, categories, headings, bold/italics, templates, citations, external-link labels): its grammar has a CJ token rule only for plain text, and every markup state matches ALPHANUM only

Bug LFV-708 · Area: analysis-common / wikipedia: WikipediaTokenizer (JFlex grammar lexical states, WikipediaTokenizerImpl.jflex) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-708.html](../../bugs/LFV-708.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You tokenize Chinese or Japanese wiki text with WikipediaTokenizer, for example '[[日本]]', '[[Category:日本]]', '== 日本 ==' or "x '''東京''' y", and expect the text inside the markup to become tokens, as it does for Latin and Korean text in the same markup ('[[Japan]]', '[[한국]]') and for Chinese/Japanese plain text ('日本' gives 日|&lt;CJ&gt; and 本|&lt;CJ&gt;). Instead every Chinese/Japanese character inside markup is skipped without an error: '[[日本]]', '[[Category:日本]]', '== 日本 ==' and '&lt;ref&gt;日本&lt;/ref&gt;' give no token at all, '[http://example.org 日本]' gives only the URL, and "x '''東京''' y" gives x and y, with y typed bold. The cause is the grammar: its only CJ token rule is for plain text (WikipediaTokenizerImpl.jflex:201), while all nine markup lexical states have a token rule for ALPHANUM only and ignore every other character (:219-309), and the CJ character class is disjoint from ALPHANUM.

## Expected vs actual

- Expected: Every Han and kana character lies inside some token, as `[[Japan]]` gives `Japan|il`: `東京は[[日本]]の首都である。` gives 11 tokens.
- Actual: `lost from "東京は[[日本]]の首都である。", tokens [東|<CJ>, 京|<CJ>, は|<CJ>, の|<CJ>, 首|<CJ>, 都|<CJ>, で|<CJ>, あ|<CJ>, る|<CJ>] expected:<> but was:<[日本]>`

## Files

- [src/org/apache/lucene/analysis/wikipedia/TestWikipediaTokenizerMarkupCjk.java](src/org/apache/lucene/analysis/wikipedia/TestWikipediaTokenizerMarkupCjk.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.wikipedia.TestWikipediaTokenizerMarkupCjk
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestWikipediaTokenizerCjkInMarkup.java](TestWikipediaTokenizerCjkInMarkup.java)
- [TestWikipediaTokenizerMarkupCjk.java](TestWikipediaTokenizerMarkupCjk.java)
- [TestWikipediaMarkupCjk.java](TestWikipediaMarkupCjk.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
