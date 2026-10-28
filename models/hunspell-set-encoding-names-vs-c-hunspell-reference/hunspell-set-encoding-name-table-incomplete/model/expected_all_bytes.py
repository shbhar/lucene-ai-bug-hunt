# Oracle table for the decode leg: for each C Hunspell encds key, the Python codec of the encoding the key names
# (Python's tables are generated from the unicode.org mapping files), and every byte 0x80..0xFF that decodes to a
# character that is not a control character (category Cc). Output: key \t byte \t codepoint.
import codecs, sys, unicodedata
py = {"iso8859%d" % i: "iso8859_%d" % i for i in range(1, 11)}
py.update({"iso885913": "iso8859_13", "iso885914": "iso8859_14", "iso885915": "iso8859_15", "iso885911": "iso8859_11",
           "tis620": "tis_620", "tis6202533": "tis_620", "koi8r": "koi8_r", "koi8u": "koi8_u", "cp1251": "cp1251",
           "microsoftcp1251": "cp1251"})
with open(sys.argv[1], "w") as out:
    for key, codec in py.items():
        n = 0
        for b in range(0x80, 0x100):
            try:
                ch = bytes([b]).decode(codec)
            except UnicodeDecodeError:
                continue
            if unicodedata.category(ch) == "Cc":
                continue
            out.write("%s\t%d\t%d\n" % (key, b, ord(ch)))
            n += 1
        print(key, codec, n, file=sys.stderr)
