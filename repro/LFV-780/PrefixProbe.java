package lfv780probe;

import java.io.Reader;
import java.io.StringReader;
import java.util.Set;
import org.apache.lucene.analysis.charfilter.HTMLStripCharFilter;

/**
 * Prints what HTMLStripCharFilter outputs for start tags whose name only begins with script,
 * style or br, for same-shape controls, and for real script/style/br tags that a fix must keep.
 * Public API only. Newlines are printed as \n.
 */
public class PrefixProbe {

  static String strip(String html, Set<String> escaped) throws Exception {
    Reader in = new StringReader(html);
    HTMLStripCharFilter f =
        escaped == null ? new HTMLStripCharFilter(in) : new HTMLStripCharFilter(in, escaped);
    StringBuilder sb = new StringBuilder();
    char[] buf = new char[7];
    int n;
    while ((n = f.read(buf, 0, buf.length)) != -1) {
      sb.append(buf, 0, n);
    }
    f.close();
    return sb.toString();
  }

  static void show(String html, Set<String> escaped) throws Exception {
    String out = strip(html, escaped).replace("\n", "\\n");
    System.out.println(
        (escaped == null ? "" : "escapedTags=" + escaped + " ") + "IN  " + html);
    System.out.println("    OUT [" + out + "]");
  }

  public static void main(String[] args) throws Exception {
    System.out.println("# prefix names (default settings)");
    show("<scripts>hello</scripts> world", null);
    show("<scrapes>hello</scrapes> world", null);
    show("<style-guide>hello</style-guide> world", null);
    show("<stale-guide>hello</stale-guide> world", null);
    show("<stylesheet>hello</stylesheet> world", null);
    show("<scripting>alpha</scripting> beta <script>f()</script> gamma", null);
    show("<p>Intro</p><script-editor lang=\"js\">Edit me</script-editor><p>Body text</p>", null);
    show("I love <scripted hut> so much", null);
    show("I love <pizza hut> so much", null);
    System.out.println("# prefix names with escapedTags");
    show("<brand>Acme</brand>", Set.of("brand"));
    show("<bland>Acme</bland>", Set.of("bland"));
    show("x<bridge>alpha</bridge>y", Set.of("br"));
    show("x<scripted>alpha</scripted>y", Set.of("script"));
    System.out.println("# real script, style and br tags (a fix must keep these)");
    show("a<script>f();</script>b", null);
    show("a<script type=\"t\">f();</script>b", null);
    show("a<style>p{}</style>b", null);
    show("one<br>two<br/>three<br class=\"x\">four", null);
    show("a<script/>f();</script>b", null);
    show("one<br title=\"a>b\">two", null);
    show("a<style title=\"a>b\">p{}</style>b", null);
    show("one<script title=\"a>b\">f();</script>two", Set.of("script"));
    show("one<br title=\"a>b\">two", Set.of("br"));
  }
}
