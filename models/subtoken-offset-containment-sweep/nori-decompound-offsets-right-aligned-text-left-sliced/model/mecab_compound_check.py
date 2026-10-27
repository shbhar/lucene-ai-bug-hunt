"""Check mecab-ko-dic Compound rows: does the concatenation of the expression surfaces equal the surface?"""
import csv, glob, os, sys
root = sys.argv[1]
n = short = longer = textdiff = 0
ex = {'short': [], 'longer': [], 'textdiff': []}
for p in sorted(glob.glob(os.path.join(root, '**', '*.csv'), recursive=True)):
    with open(p, encoding='utf-8') as f:
        for row in csv.reader(f):
            if len(row) < 12 or row[8] != 'Compound' or row[11] == '*':
                continue
            n += 1
            parts = [t.split('/')[0] for t in row[11].split('+')]
            cat = ''.join(x.strip() for x in parts)
            if len(cat) < len(row[0]):
                short += 1; ex['short'].append((row[0], row[11], os.path.basename(p)))
            elif len(cat) > len(row[0]):
                longer += 1; ex['longer'].append((row[0], row[11], os.path.basename(p)))
            elif cat != row[0]:
                textdiff += 1; ex['textdiff'].append((row[0], row[11], os.path.basename(p)))
print(f'compound rows={n} shorter={short} longer={longer} same-length-different-text={textdiff}')
for k, v in ex.items():
    for e in v[:15]:
        print(k, *e)
