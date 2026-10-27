# MoreLikeThisQuery ignores its moreLikeFields and searches only fieldName, so the XML parser's &lt;LikeThisQuery fieldNames="a,b"&gt; searches only the first field (documents similar through the other fields are silently missed, every release since 6.1.0)

Bug LFV-556 · Area: queries / mlt (MoreLikeThisQuery.rewrite over MoreLikeThis.like(String, Reader...)) x queryparser / xml (LikeThisQueryBuilder) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a MoreLikeThisQuery for the text 'zebra quokka' with moreLikeFields {title, body} (or parse &lt;LikeThisQuery fieldNames="title,body"&gt;zebra quokka&lt;/LikeThisQuery&gt;) on an index where those words occur only in body, and expect the documents whose body contains them, because the javadoc says moreLikeFields are the 'fields used for similarity measure' and the XML DTD calls fieldNames a 'Comma delimited list of field names'. Instead the query rewrites to terms of fieldName only (title, the first listed field on the XML path), here an empty query, so it matches 0 documents instead of 2; the field list changes nothing about matching or scoring (only equals and hashCode), and on the XML path the result depends on the order of the list ('title,body' 0 hits, 'body,title' 2). Up to 6.0.1 each word went to the listed field with the highest document frequency; LUCENE-6954 (6.1) made MoreLikeThis's Reader path use only the field it is given, and MoreLikeThisQuery, which uses that path, was never adapted.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The query rewrites to terms of fieldName only (title, the first listed field on the XML path), here an empty query, so it matches 0 documents instead of 2; the field list changes nothing about matching or scoring (only equals and hashCode), and on the XML path the result depends on the order of the list ('title,body' 0 hits, 'body,title' 2).

## Files

- [src/org/apache/lucene/queries/mlt/TestMoreLikeThisQueryMoreLikeFields.java](src/org/apache/lucene/queries/mlt/TestMoreLikeThisQueryMoreLikeFields.java) (JUnit test)

## Run it

- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.mlt.TestMoreLikeThisQueryMoreLikeFields
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
