package probe;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.apache.lucene.document.DateTools;

/** Prints DateTools strings for dates inside and outside the years 1..9999 and their sort order. */
public class DateToolsYearProbe {
  static final TimeZone GMT = TimeZone.getTimeZone("GMT");

  static long gmt(int era, int year, int month, int day) {
    GregorianCalendar c = new GregorianCalendar(GMT, Locale.ROOT);
    c.clear();
    c.set(Calendar.ERA, era);
    c.set(year, month, day);
    return c.getTimeInMillis();
  }

  static String show(long t) {
    GregorianCalendar c = new GregorianCalendar(GMT, Locale.ROOT);
    c.setTimeInMillis(t);
    return String.format(
        Locale.ROOT,
        "%d %s-%02d-%02d",
        c.get(Calendar.YEAR),
        c.get(Calendar.ERA) == GregorianCalendar.BC ? "BC" : "AD",
        c.get(Calendar.MONTH) + 1,
        c.get(Calendar.DAY_OF_MONTH));
  }

  static void line(String label, long t, DateTools.Resolution r) {
    String s = DateTools.timeToString(t, r);
    String back;
    try {
      back = show(DateTools.stringToTime(s));
    } catch (Exception e) {
      back = e.getClass().getSimpleName() + ": " + e.getMessage();
    }
    System.out.println("STR " + r + " " + label + " -> \"" + s + "\" -> reads back as " + back);
  }

  public static void main(String[] args) {
    int AD = GregorianCalendar.AD, BC = GregorianCalendar.BC;
    DateTools.Resolution DAY = DateTools.Resolution.DAY, YEAR = DateTools.Resolution.YEAR;
    line("2 BC-01-01", gmt(BC, 2, 0, 1), DAY);
    line("AD 2-01-01", gmt(AD, 2, 0, 1), DAY);
    line("AD 1-01-01", gmt(AD, 1, 0, 1), DAY);
    line("1 BC-06-01", gmt(BC, 1, 5, 1), YEAR);
    line("AD 1-06-01", gmt(AD, 1, 5, 1), YEAR);
    line("1200 BC-06-01", gmt(BC, 1200, 5, 1), DAY);
    line("AD 1200-06-01", gmt(AD, 1200, 5, 1), DAY);
    line("AD 999-12-31", gmt(AD, 999, 11, 31), DAY);
    line("AD 1000-01-01", gmt(AD, 1000, 0, 1), DAY);
    line("AD 2026-01-01", gmt(AD, 2026, 0, 1), DAY);
    line("AD 9999-12-31", gmt(AD, 9999, 11, 31), DAY);
    line("AD 10000-01-01", gmt(AD, 10000, 0, 1), DAY);
    // sort the strings of dates given in date order; print the string order
    String[] labels = {
      "2 BC-01-01", "AD 1-01-01", "AD 999-12-31", "AD 1000-01-01", "AD 2026-01-01", "AD 9999-12-31",
      "AD 10000-01-01"
    };
    long[] times = {
      gmt(BC, 2, 0, 1), gmt(AD, 1, 0, 1), gmt(AD, 999, 11, 31), gmt(AD, 1000, 0, 1),
      gmt(AD, 2026, 0, 1), gmt(AD, 9999, 11, 31), gmt(AD, 10000, 0, 1)
    };
    List<String> sorted = new ArrayList<>();
    for (int i = 0; i < times.length; i++) {
      sorted.add(DateTools.timeToString(times[i], DAY) + " = " + labels[i]);
    }
    sorted.sort(null);
    System.out.println("DATE ORDER  " + String.join(" < ", labels));
    System.out.println("STRING ORDER " + String.join(" < ", sorted));
  }
}
