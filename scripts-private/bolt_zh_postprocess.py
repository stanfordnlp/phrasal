import sys, re

numerical_dashes = [r'([0-9])-([0-9])',r'\1 - \2']
letter_dashes = [r'([a-z]) - ([a-z])',r'\1-\2']
us_money = [r'([0-9.,]+ (?:thou|mill|bill|tril)[^ ]+ doll)',r'us $ \1']
rmb_money = [r'([0-9.,]+ (?:(?:thou|mill|bill|tril)[^ ]+ )?(?:yuan))',r'rmb \1']

patterns = [numerical_dashes, letter_dashes, us_money, rmb_money]

for line in open(sys.argv[1]):
    for pat in patterns:
        line = re.sub(pat[0], pat[1], line)
    print line,
