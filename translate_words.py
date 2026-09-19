import urllib.request
import urllib.parse
import json
import time
import os

# خوندن کلمات انگلیسی
with open('assets/english_words.txt', 'r') as f:
    words = [w.strip() for w in f.readlines() if w.strip()]

# حذف تکراری‌ها
words = list(dict.fromkeys(words))
print(f"تعداد کلمات: {len(words)}")

output_file = 'assets/words.txt'
start_index = 0

# اگه فایل قبلی وجود داره، از جایی که مونده ادامه بده
if os.path.exists(output_file):
    with open(output_file, 'r') as f:
        existing = f.readlines()
    start_index = len(existing)
    print(f"ادامه از کلمه شماره {start_index}")

out = open(output_file, 'a', encoding='utf-8')

success_count = 0
fail_count = 0

for i in range(start_index, len(words)):
    word = words[i]
    
    if len(word) < 2 or not word.isalpha():
        continue
    
    try:
        url = f"https://api.mymemory.translated.net/get?q={urllib.parse.quote(word)}&langpair=en|fa"
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        
        with urllib.request.urlopen(req, timeout=10) as response:
            data = json.loads(response.read().decode('utf-8'))
            translation = data.get('responseData', {}).get('translatedText', '')
            
            if translation and translation.lower() != word.lower():
                out.write(f"{word}|{translation}\n")
                out.flush()
                success_count += 1
            else:
                fail_count += 1
    except Exception as e:
        fail_count += 1
    
    time.sleep(0.2)
    
    if (i + 1) % 50 == 0:
        print(f"پیشرفت: {i+1}/{len(words)} | موفق: {success_count} | ناموفق: {fail_count}")
        print(f"  آخرین کلمه: {word} = {translation if 'translation' in dir() else '?'}")

out.close()
print(f"\n✅ تمام شد! موفق: {success_count} | ناموفق: {fail_count}")
