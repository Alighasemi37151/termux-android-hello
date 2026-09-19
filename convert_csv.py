import csv

with open('assets/dictionary.csv', 'r', encoding='utf-8') as f:
    reader = csv.DictReader(f)
    seen = set()
    count = 0
    with open('assets/words.txt', 'w', encoding='utf-8') as f_out:
        for row in reader:
            eng = row.get('english', '').strip().lower()
            per = row.get('persian', '').strip()
            if eng and per and eng not in seen:
                seen.add(eng)
                f_out.write(eng + "|" + per + "\n")
                count += 1
    print("Saved: " + str(count) + " unique words")
