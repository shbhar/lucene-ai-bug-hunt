// Offline check of the issue references of one CHANGES.txt section (single-file program, JDK 17+).
// usage: java ChangesIssueRefCheck.java <CHANGES.txt> <section version> <file of issue/PR numbers named in the
//        branch's commit subjects, one per line>
// Every GITHUB#N in the section must be <= the highest number any commit of the release branch names
// (an issue or PR cannot be newer than the branch it is released from). Exit 1 if some reference is newer.
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class ChangesIssueRefCheck {
  public static void main(String[] a) throws Exception {
    List<String> lines = Files.readAllLines(Path.of(a[0]));
    String head = "Lucene " + a[1] + " ";
    long max = 0;
    for (String l : Files.readAllLines(Path.of(a[2]))) if (!l.isBlank()) max = Math.max(max, Long.parseLong(l.trim()));
    boolean in = false;
    int bad = 0, refs = 0;
    for (int i = 0; i < lines.size(); i++) {
      String l = lines.get(i);
      if (l.startsWith("=") && l.contains(" Lucene ")) {
        if (in) break;
        in = l.contains(head);
        continue;
      }
      if (!in) continue;
      Matcher m = Pattern.compile("GITHUB#(\\d+)").matcher(l);
      while (m.find()) {
        refs++;
        long n = Long.parseLong(m.group(1));
        if (n > max) {
          bad++;
          System.out.println("line " + (i + 1) + ": GITHUB#" + n + " is newer than every issue/PR the branch names (max #" + max + "): " + l.trim());
        }
      }
    }
    System.out.println("section " + a[1] + ": " + refs + " GITHUB# references, " + bad + " impossible (max named by branch commits: #" + max + ")");
    System.exit(bad == 0 ? 0 : 1);
  }
}
