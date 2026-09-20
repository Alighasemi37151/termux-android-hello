package com.example.hello;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.DialogInterface;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import java.io.IOException;
import android.provider.MediaStore;
import android.graphics.Bitmap;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.common.InputImage;

public class MainActivity extends Activity {

    public static class MyDatabase extends SQLiteOpenHelper {
        private static final String DB_NAME = "dictionary_v12.db";
        private static final int DB_VERSION = 1;

        private Context context;

        public MyDatabase(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
            this.context = context;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE dictionary (english TEXT UNIQUE, persian TEXT)");
            loadWordsFromAssets(db);
        }

        private void insertWord(SQLiteDatabase db, String eng, String per) {
            ContentValues values = new ContentValues();
            values.put("english", eng);
            values.put("persian", per);
            db.insert("dictionary", null, values);
        }

        public int getWordCount() {
            SQLiteDatabase db = this.getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM dictionary", null);
            int count = 0;
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
            cursor.close();
            return count;
        }

        public String getMeaning(String word) {
            SQLiteDatabase db = this.getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT persian FROM dictionary WHERE english = ?", new String[]{word.toLowerCase()});
            if (cursor.moveToFirst()) {
                String result = cursor.getString(0);
                cursor.close();
                return result;
            }
            cursor.close();
            return null;
        }

        public void saveMeaning(String english, String persian) {
            try {
                SQLiteDatabase db = this.getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put("english", english.toLowerCase());
                values.put("persian", persian);
                db.insertWithOnConflict("dictionary", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                db.close();
            } catch (Exception e) {}
        }

        private void loadWordsFromAssets(SQLiteDatabase db) {
            int count = 0;
            try {
                java.io.InputStream is = context.getAssets().open("words.txt");
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is), 8192);
                String line;
                db.beginTransaction();
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    int pipeIdx = line.indexOf('|');
                    if (pipeIdx > 0) {
                        String eng = line.substring(0, pipeIdx).trim().toLowerCase();
                        String per = line.substring(pipeIdx + 1).trim();
                        if (!eng.isEmpty() && !per.isEmpty()) {
                            ContentValues values = new ContentValues();
                            values.put("english", eng);
                            values.put("persian", per);
                            long result = db.insertWithOnConflict("dictionary", null, values, SQLiteDatabase.CONFLICT_IGNORE);
                            if (result != -1) count++;
                        }
                    }
                }
                db.setTransactionSuccessful();
                db.endTransaction();
                reader.close();
                is.close();
                // ذخیره تعداد در یه فایل
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("/sdcard/word_count.txt");
                    fw.write("Loaded: " + count);
                    fw.close();
                } catch (Exception e) {}
                android.util.Log.d("DICT", "Loaded " + count + " words");
            } catch (Exception e) {
                android.util.Log.e("DICT", "Error: " + e.getMessage());
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS dictionary");
            onCreate(db);
        }
    }

    public static WordDetectionService accessibilityServiceInstance = null;

    public static class FloatingBubbleService extends Service {
        private WindowManager windowManager;
        private View bubbleView;
        private View magnifierView;
        private View popupView;
        private MyDatabase db;
        private static final int BUBBLE_SIZE = 50;
        private static final int MAGNIFIER_SIZE = 15;
        private static final int MAGNIFIER_OFFSET_Y = 80;
        private Handler handler = new Handler(Looper.getMainLooper());
        private Runnable translationRunnable;
        private Runnable hidePopupRunnable;
        private String lastTranslatedWord = "";

        @Override
        public void onCreate() {
            super.onCreate();
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            db = new MyDatabase(this);
            createMagnifier();
            createBubble();
        }

        private void createMagnifier() {
            TextView magnifier = new TextView(this);
            magnifier.setText("+");
            magnifier.setTextSize(24);
            magnifier.setTextColor(Color.WHITE);
            magnifier.setGravity(Gravity.CENTER);

            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(Color.argb(150, 0, 0, 0));
            // دایره زرد حذف شد
            shape.setSize(MAGNIFIER_SIZE, MAGNIFIER_SIZE);
            magnifier.setBackground(shape);

            int layoutType;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                layoutType = WindowManager.LayoutParams.TYPE_PHONE;
            }

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    MAGNIFIER_SIZE, MAGNIFIER_SIZE, layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 90;
            params.y = 280;

            magnifierView = magnifier;
            magnifierView.setAlpha(0.5f);
            windowManager.addView(magnifierView, params);
        }

        private void createBubble() {
            TextView bubble = new TextView(this);
            bubble.setText("T");
            bubble.setTextSize(10);
            bubble.setTextColor(Color.WHITE);
            bubble.setGravity(Gravity.CENTER);

            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(Color.parseColor("#A5D6A7"));
            shape.setSize(BUBBLE_SIZE, BUBBLE_SIZE);
            bubble.setBackground(shape);

            int layoutType;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                layoutType = WindowManager.LayoutParams.TYPE_PHONE;
            }

            final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    BUBBLE_SIZE, BUBBLE_SIZE, layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 100;
            params.y = 200;

            bubble.setOnTouchListener(new View.OnTouchListener() {
                private int initialX, initialY;
                private float initialTouchX, initialTouchY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = params.x;
                            initialY = params.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            if (translationRunnable != null) handler.removeCallbacks(translationRunnable);
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            params.x = initialX + (int) (event.getRawX() - initialTouchX);
                            params.y = initialY + (int) (event.getRawY() - initialTouchY);
                            windowManager.updateViewLayout(bubbleView, params);

                            WindowManager.LayoutParams magParams = (WindowManager.LayoutParams) magnifierView.getLayoutParams();
                            magParams.x = params.x + (BUBBLE_SIZE - MAGNIFIER_SIZE) / 2;
                            magParams.y = params.y - MAGNIFIER_OFFSET_Y;
                            windowManager.updateViewLayout(magnifierView, magParams);

                            if (translationRunnable != null) handler.removeCallbacks(translationRunnable);
                            translationRunnable = new Runnable() {
                                @Override
                                public void run() { translateAtPosition(params.x, params.y); }
                            };
                            handler.postDelayed(translationRunnable, 300);
                            return true;
                        case MotionEvent.ACTION_UP:
                            if (translationRunnable != null) handler.removeCallbacks(translationRunnable);
                            translateAtPosition(params.x, params.y);
                            return true;
                    }
                    return false;
                }
            });

            bubbleView = bubble;
            windowManager.addView(bubbleView, params);
        }

        private void translateAtPosition(int x, int y) {
            if (accessibilityServiceInstance == null) return;
            int magX = x + (BUBBLE_SIZE - MAGNIFIER_SIZE) / 2 + MAGNIFIER_SIZE / 2;
            int magY = (y - MAGNIFIER_OFFSET_Y) + MAGNIFIER_SIZE / 2;
            String word = accessibilityServiceInstance.getWordAt(magX, magY);
            if (word == null || word.isEmpty()) return;
            if (word.equals(lastTranslatedWord)) return;
            String meaning = db.getMeaning(word);
            if (meaning == null) return;
            lastTranslatedWord = word;
            showTranslationPopup(word, meaning);
        }

        public void showTranslationPopup(String word, String meaning) {
            if (popupView != null) {
                windowManager.removeView(popupView);
                popupView = null;
            }
            if (hidePopupRunnable != null) handler.removeCallbacks(hidePopupRunnable);

            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setBackgroundColor(Color.WHITE);
            layout.setPadding(30, 30, 30, 30);

            TextView wordText = new TextView(this);
            wordText.setText(word);
            wordText.setTextSize(18);
            wordText.setTextColor(Color.BLACK);

            TextView meaningText = new TextView(this);
            meaningText.setText(meaning);
            meaningText.setTextSize(16);
            meaningText.setTextColor(Color.DKGRAY);
            meaningText.setPadding(0, 10, 0, 10);

            // layout.addView(wordText); // متن اصلی حذف شد
            layout.addView(meaningText);

            int layoutType;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                layoutType = WindowManager.LayoutParams.TYPE_PHONE;
            }

            WindowManager.LayoutParams popupParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            popupParams.gravity = Gravity.TOP | Gravity.START;
            popupParams.x = 100;
            popupParams.y = 500;

            popupView = layout;
            windowManager.addView(popupView, popupParams);

            hidePopupRunnable = new Runnable() {
                @Override
                public void run() {
                    if (popupView != null) {
                        try { windowManager.removeView(popupView); } catch (Exception e) {}
                        popupView = null;
                    }
                }
            };
            handler.postDelayed(hidePopupRunnable, 3000);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (bubbleView != null) windowManager.removeView(bubbleView);
            if (magnifierView != null) windowManager.removeView(magnifierView);
            if (popupView != null) windowManager.removeView(popupView);
        }

        @Override
        public IBinder onBind(Intent intent) { return null; }
    }

    public static class WordDetectionService extends AccessibilityService {
        @Override
        public void onAccessibilityEvent(AccessibilityEvent event) {
            // غیرفعال شده - به ClipboardListenerService منتقل شد
        }
        private MyDatabase db;
        private String lastClipboard = "";
        public static boolean translationEnabled = true;
        private String lastGoogleError = null;
        private Thread currentThread = null;

        @Override
        public void onServiceConnected() {
            super.onServiceConnected();
            db = new MyDatabase(this);
            accessibilityServiceInstance = this;
            int wc = db.getWordCount();
            String forceM = db.getMeaning("force");
            String abandonM = db.getMeaning("abandon");
            String helloM = db.getMeaning("hello");
            String msg = "Words: " + wc + " force: " + forceM + " abandon: " + abandonM + " hello: " + helloM;
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        }

        private void onAccessibilityEvent_DISABLED(AccessibilityEvent event) {
            if (!translationEnabled) return;
            int eventType = event.getEventType();
            if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
                AccessibilityNodeInfo source = event.getSource();
                if (source != null) {
                    Rect rect = new Rect();
                    source.getBoundsInScreen(rect);
                }
            }
        }

        private void checkClipboardNow() {
            try {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip()) {
                    android.content.ClipData clip = cm.getPrimaryClip();
                    if (clip != null && clip.getItemCount() > 0) {
                        CharSequence text = clip.getItemAt(0).getText();
                        if (text != null) {
                            String copied = text.toString().trim();
                            if (!copied.isEmpty() && !copied.equals(lastClipboard)) {
                                lastClipboard = copied;
                                if (!translationEnabled) return;
                                String cleanWord = copied.toLowerCase().replaceAll("[^a-z]", "");
                                if (!cleanWord.isEmpty() && cleanWord.length() > 1) {
                                    // اگه کلمه کوتاهه، مثل قبل
                                    if (cleanWord.length() <= 25) {
                                        showClipboardPopup(cleanWord, null);
                                    } else {
                                        // اگه متن طولانیه، کلمه‌به‌کلمه ترجمه کن
                                        showLongTextTranslation(cleanWord);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {}
        }

        private void showClipboardPopup(final String word, String meaning) {
            // همیشه اول از گوگل می‌پرسیم
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    String gMeaning = null;
                    try {
                        gMeaning = getGoogleTranslation(word);
                    } catch (Exception e) {
                        gMeaning = null;
                    }
                    
                    final String finalG = gMeaning;
                    Handler h = new Handler(Looper.getMainLooper());
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            if (finalG != null && finalG.length() > 0) {
                                // گوگل جواب داد
                                showPopupOnUI(word, finalG);
                            } else {
                                // گوگل جواب نداد، از دیتابیس استفاده کن
                                String dbMeaning = db.getMeaning(word);
                                if (dbMeaning != null) {
                                    showPopupOnUI(word, dbMeaning);
                                }
                            }
                        }
                    });
                }
            });
            t.start();
        }

        private void showPopupOnUI(final String word, final String meaning) {
            Handler h = new Handler(Looper.getMainLooper());
            h.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                        if (wm == null) return;
                        LinearLayout layout = new LinearLayout(WordDetectionService.this);
                        layout.setOrientation(LinearLayout.VERTICAL);
                        layout.setBackgroundColor(Color.WHITE);
                        layout.setPadding(40, 30, 40, 30);
                        TextView wordText = new TextView(WordDetectionService.this);
                        wordText.setText(word);
                        wordText.setTextSize(20);
                        wordText.setTextColor(Color.BLACK);
                        TextView meaningText = new TextView(WordDetectionService.this);
                        meaningText.setText(meaning);
                        meaningText.setTextSize(18);
                        meaningText.setTextColor(Color.DKGRAY);
                        meaningText.setPadding(0, 15, 0, 15);
                        // layout.addView(wordText); // متن اصلی حذف شد
                        layout.addView(meaningText);
                        int layoutType;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                        } else {
                            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
                        }
                        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                                WindowManager.LayoutParams.WRAP_CONTENT,
                                WindowManager.LayoutParams.WRAP_CONTENT,
                                layoutType,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                                PixelFormat.TRANSLUCENT);
                        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                        params.y = 200;
                        final View popupView = layout;
                        wm.addView(popupView, params);
                        Handler h2 = new Handler(Looper.getMainLooper());
                        h2.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                try { wm.removeView(popupView); } catch (Exception e) {}
                            }
                        }, 4000);
                    } catch (Exception e) {}
                }
            });
        }

        public String getWordAt(int x, int y) {
            try {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root == null) return null;
                return findWordInNode(root, x, y);
            } catch (Exception e) {
                return null;
            }
        }

        private String findWordInNode(AccessibilityNodeInfo node, int x, int y) {
            if (node == null) return null;
            Rect rect = new Rect();
            node.getBoundsInScreen(rect);
            if (rect.contains(x, y)) {
                if (node.getText() != null) {
                    String text = node.getText().toString();
                    String word = extractWordAtPosition(text, rect, x, y);
                    if (word != null && word.matches("[a-zA-Z]+")) return word.toLowerCase();
                }
                if (node.getContentDescription() != null) {
                    String desc = node.getContentDescription().toString();
                    String word = extractWordAtPosition(desc, rect, x, y);
                    if (word != null && word.matches("[a-zA-Z]+")) return word.toLowerCase();
                }
                for (int i = 0; i < node.getChildCount(); i++) {
                    String result = findWordInNode(node.getChild(i), x, y);
                    if (result != null) return result;
                }
            }
            return null;
        }

        private String extractWordAtPosition(String text, Rect rect, int x, int y) {
            if (text == null || text.isEmpty() || rect.width() == 0) return null;
            int relativeX = x - rect.left;
            float charWidth = (float) rect.width() / text.length();
            int charIndex = (int) (relativeX / charWidth);
            if (charIndex < 0) charIndex = 0;
            if (charIndex >= text.length()) charIndex = text.length() - 1;
            if (!Character.isLetter(text.charAt(charIndex))) {
                int leftIdx = charIndex;
                int rightIdx = charIndex;
                while (leftIdx > 0 && !Character.isLetter(text.charAt(leftIdx))) leftIdx--;
                while (rightIdx < text.length() - 1 && !Character.isLetter(text.charAt(rightIdx))) rightIdx++;
                if (Character.isLetter(text.charAt(leftIdx))) charIndex = leftIdx;
                else if (Character.isLetter(text.charAt(rightIdx))) charIndex = rightIdx;
                else return null;
            }
            int start = charIndex;
            int end = charIndex;
            while (start > 0 && Character.isLetter(text.charAt(start - 1))) start--;
            while (end < text.length() - 1 && Character.isLetter(text.charAt(end + 1))) end++;
            if (start <= end && end < text.length()) {
                return text.substring(start, end + 1);
            }
            return null;
        }

        public String getGoogleTranslation(String word) {
            try {
                String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=" + getSavedLanguage() + "&dt=t&q=" + java.net.URLEncoder.encode(word, "UTF-8");
                android.util.Log.d("GOOGLE", "URL: " + url);
                java.net.URL obj = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) obj.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(1500);
                conn.setReadTimeout(1500);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                String json = response.toString();
                int start = json.indexOf("[[[\"");
                if (start == -1) {
                    lastGoogleError = "JSON format error";
                    return null;
                }
                start += 4;
                int end = json.indexOf("\"", start);
                if (end == -1) {
                    lastGoogleError = "End quote not found";
                    return null;
                }
                String result = json.substring(start, end);
                result = result.replace("\\u200c", "\u200c");
                android.util.Log.d("GOOGLE", "Result: " + result);
                return result;
            } catch (Exception e) {
                lastGoogleError = e.getClass().getSimpleName() + ": " + e.getMessage();
                return null;
            }
        }


        private void showLongTextTranslation(final String text) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String spacedText = text;
                        if (!text.contains(" ") && text.length() > 15) {
                            spacedText = addSpaces(text);
                        }
                        String translated = getGoogleTranslation(spacedText);
                        if (translated == null || translated.length() == 0) {
                            Handler h = new Handler(Looper.getMainLooper());
                            h.post(new Runnable() {
                                @Override
                                public void run() {
                                    showPopupOnUI("دیکشنری کوچک", "لطفاً اینترنت رو چک کن");
                                }
                            });
                            return;
                        }
                        final String finalT = translated;
                        final String finalOriginal = spacedText;
                        Handler h = new Handler(Looper.getMainLooper());
                        h.post(new Runnable() {
                            @Override
                            public void run() {
                                showTextTranslationPopup(finalOriginal, finalT);
                            }
                        });
                    } catch (Exception e) {}
                }
            }).start();
        }

        private void showTextTranslationPopup(String original, String translated) {
            try {
                final WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                if (wm == null) return;
                
                android.widget.ScrollView scroll = new android.widget.ScrollView(WordDetectionService.this);
                LinearLayout layout = new LinearLayout(WordDetectionService.this);
                layout.setOrientation(LinearLayout.VERTICAL);
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.RECTANGLE);
                bg.setColor(Color.argb(240, 255, 255, 255));
                bg.setCornerRadius(30);
                bg.setStroke(2, Color.argb(100, 200, 200, 200));
                layout.setBackground(bg);
                layout.setPadding(40, 30, 40, 30);
                
                TextView transText = new TextView(WordDetectionService.this);
                transText.setText(translated);
                transText.setTextSize(18);
                transText.setTextColor(Color.BLACK);
                transText.setLineSpacing(10, 1.2f);
                layout.addView(transText);
                
                scroll.addView(layout);
                
                int layoutType;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                } else {
                    layoutType = WindowManager.LayoutParams.TYPE_PHONE;
                }
                
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                
                final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        (int)(screenWidth * 0.9),
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        layoutType,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                        PixelFormat.TRANSLUCENT);
                params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                params.y = 150;
                
                final View popupView = scroll;
                wm.addView(popupView, params);
                
                scroll.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, android.view.MotionEvent event) {
                        if (event.getAction() == android.view.MotionEvent.ACTION_OUTSIDE) {
                            try { wm.removeView(popupView); } catch (Exception e) {}
                            return true;
                        }
                        return false;
                    }
                });

                scroll.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try { wm.removeView(popupView); } catch (Exception e) {}
                    }
                });
            } catch (Exception e) {}
        }

        

        private String addSpaces(String text) {
            if (text.contains(" ")) return text;
            StringBuilder result = new StringBuilder();
            String lower = text.toLowerCase();
            int i = 0;
            while (i < lower.length()) {
                boolean found = false;
                for (int len = Math.min(20, lower.length() - i); len >= 3; len--) {
                    String candidate = lower.substring(i, i + len);
                    if (db.getMeaning(candidate) != null) {
                        if (result.length() > 0) result.append(" ");
                        result.append(candidate);
                        i += len;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    if (result.length() > 0 && !result.toString().endsWith(" ")) {
                        result.append(" ");
                    }
                    result.append(lower.charAt(i));
                    i++;
                }
            }
            return result.toString();
        }

        @Override
        public void onInterrupt() {}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final TextView resultText = findViewById(R.id.resultText);
        Button translateButton = findViewById(R.id.translateButton);
        Button manualTranslateButton = findViewById(R.id.manualTranslateButton);

        // ========== دکمه انتخاب زبان ==========
        Button languageButton = findViewById(R.id.languageButton);
        languageButton.setText("انتخاب زبان (" + getSavedLanguageName() + ")");
        languageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLanguageDialog(languageButton);
            }
        });


        // ========== دکمه خاموش کردن حباب ==========
        Button stopButton = findViewById(R.id.stopButton);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent serviceIntent = new Intent(MainActivity.this, FloatingBubbleService.class);
                stopService(serviceIntent);
                Intent clipboardIntent = new Intent(MainActivity.this, ClipboardListenerService.class);
                stopService(clipboardIntent);
                resultText.setText("حباب و ذره‌بین خاموش شدند.");
            }
        });

        // ========== دکمه ترجمه کل صفحه (OCR) ==========
        manualTranslateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImageFromGallery();
            }
        });

        Button toggleTranslateButton = findViewById(R.id.toggleTranslateButton);
        toggleTranslateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (WordDetectionService.translationEnabled) {
                    WordDetectionService.translationEnabled = false;
                    toggleTranslateButton.setText("روشن کردن ترجمه");
                    resultText.setText("ترجمه خاموش شد.");
                } else {
                    WordDetectionService.translationEnabled = true;
                    toggleTranslateButton.setText("خاموش کردن ترجمه");
                    resultText.setText("ترجمه روشن شد.");
                }
            }
        });

        translateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!Settings.canDrawOverlays(MainActivity.this)) {
                        Toast.makeText(MainActivity.this, "دسترسی Overlay غیرفعاله!", Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                        return;
                    }
                }

                Intent serviceIntent = new Intent(MainActivity.this, FloatingBubbleService.class);
                startService(serviceIntent);
                Intent clipboardIntent = new Intent(MainActivity.this, ClipboardListenerService.class);
                startService(clipboardIntent);
                resultText.setText("حباب و ذره‌بین فعال شدند!\nذره‌بین رو روی کلمه ببر.");
            }
        });

        // ========== دکمه‌های OCR ==========
        Button scanFromGalleryButton = findViewById(R.id.scanFromGalleryButton);
        Button libraryButton = findViewById(R.id.libraryButton);

        scanFromGalleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImageFromGallery();
            }
        });

        libraryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resultText.setText("کتابخانه متن (در حال ساخت...)");
            }
        });
    }

    
    
    // ========== متدهای انتخاب زبان ==========
    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_LANGUAGE = "selected_language";

    public static String getSavedLanguage() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(KEY_LANGUAGE, "fa");
    }

    public static String getSavedLanguageName() {
        String code = getSavedLanguage();
        switch (code) {
            case "fa": return "فارسی";
            case "en": return "انگلیسی";
            case "de": return "آلمانی";
            case "fr": return "فرانسوی";
            case "tr": return "ترکی";
            case "zh": return "چینی";
            default: return "فارسی";
        }
    }

    private void showLanguageDialog(final Button languageButton) {
        final String[] languages = {"فارسی", "انگلیسی", "آلمانی", "فرانسوی", "ترکی", "چینی"};
        final String[] codes = {"fa", "en", "de", "fr", "tr", "zh"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("انتخاب زبان ترجمه");
        builder.setItems(languages, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                prefs.edit().putString(KEY_LANGUAGE, codes[which]).apply();
                languageButton.setText("انتخاب زبان (" + languages[which] + ")");
                Toast.makeText(MainActivity.this, "زبان به " + languages[which] + " تغییر کرد", Toast.LENGTH_SHORT).show();
            }
        });
        builder.show();
    }


    // ========== متدهای OCR (ML Kit) ==========
    private static final int PICK_IMAGE_REQUEST = 1;

    private void pickImageFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            extractTextFromImage(imageUri);
        }
    }

    private void extractTextFromImage(Uri imageUri) {
        try {
            final TextView resultText = findViewById(R.id.resultText);
            resultText.setText("در حال استخراج متن...");

            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
            InputImage image = InputImage.fromBitmap(bitmap, 0);

            com.google.mlkit.vision.text.TextRecognizer recognizer =
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        String extractedText = visionText.getText();
                        if (extractedText.isEmpty()) {
                            resultText.setText("متنی در تصویر پیدا نشد.");
                        } else {
                            resultText.setText("متن استخراج شده:\n\n" + extractedText);
                        }
                    })
                    .addOnFailureListener(e -> {
                        resultText.setText("خطا در استخراج متن: " + e.getMessage());
                    });
        } catch (IOException e) {
            e.printStackTrace();
            TextView resultText = findViewById(R.id.resultText);
            resultText.setText("خطا در بارگذاری تصویر: " + e.getMessage());
        }
    }

    // ========== سرویس گوش دادن به کلیپ‌بورد ==========
    public static class ClipboardListenerService extends Service {
        public String getGoogleTranslation(String word) {
            try {
                String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=" + getSavedLanguage() + "&dt=t&q=" + java.net.URLEncoder.encode(word, "UTF-8");
                java.net.URL obj = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) obj.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(1500);
                conn.setReadTimeout(1500);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                String json = response.toString();
                int start = json.indexOf("[[[\"");
                if (start == -1) return null;
                start += 4;
                int end = json.indexOf("\"", start);
                if (end == -1) return null;
                String result = json.substring(start, end);
                result = result.replace("\\u200c", "\u200c");
                return result;
            } catch (Exception e) {
                return null;
            }
        }
        private String wrapText(String text, int wordsPerLine) {
            String[] words = text.split(" ");
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < words.length; i++) {
                result.append(words[i]);
                if ((i + 1) % wordsPerLine == 0) {
                    result.append("\n");
                } else if (i < words.length - 1) {
                    result.append(" ");
                }
            }
            return result.toString();
        }
        private MyDatabase db;
        private android.speech.tts.TextToSpeech tts;
        private WindowManager windowManager;
        private View popupView;
        private Handler handler = new Handler(Looper.getMainLooper());
        private String lastClipboard = "";
        private Runnable clipboardRunnable;
        private Runnable hidePopupRunnable;

        @Override
        public void onCreate() {
            super.onCreate();
            db = new MyDatabase(this);
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            startClipboardListener();
            tts = new android.speech.tts.TextToSpeech(this, new android.speech.tts.TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                        tts.setLanguage(java.util.Locale.US);
                    }
                }
            });
        }

        private void startClipboardListener() {
            clipboardRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null && cm.hasPrimaryClip()) {
                            android.content.ClipData clip = cm.getPrimaryClip();
                            if (clip != null && clip.getItemCount() > 0) {
                                CharSequence text = clip.getItemAt(0).getText();
                                if (text != null) {
                                    String copied = text.toString().trim();
                                    if (!copied.isEmpty() && !copied.equals(lastClipboard)) {
                                        lastClipboard = copied;
                                        handleCopiedText(copied);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {}
                    handler.postDelayed(clipboardRunnable, 1000);
                }
            };
            handler.post(clipboardRunnable);
        }

        private void handleCopiedText(final String text) {
            if (!WordDetectionService.translationEnabled) return;
            final String cleanWord = text.toLowerCase().replaceAll("[^a-z\\s]", " ").trim();
            if (cleanWord.isEmpty()) return;

            new Thread(new Runnable() {
                @Override
                public void run() {
                    String meaning = null;
                    try {
                        meaning = getGoogleTranslation(cleanWord);
                    } catch (Exception e) {
                        meaning = null;
                    }

                    final String finalMeaning;
                    if (meaning != null && meaning.length() > 0 && !meaning.equals(cleanWord)) {
                        String dbMeaning = db.getMeaning(cleanWord);
                        if (dbMeaning == null || !dbMeaning.equals(meaning)) {
                            db.saveMeaning(cleanWord, meaning);
                        }
                        finalMeaning = meaning;
                    } else {
                        finalMeaning = db.getMeaning(cleanWord);
                    }

                    if (finalMeaning != null) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                showPopup(cleanWord, finalMeaning);
                            }
                        });
                    }
                }
            }).start();
        }

        private void showPopup(String word, String meaning) {
            if (popupView != null) {
                try { windowManager.removeView(popupView); } catch (Exception e) {}
                popupView = null;
            }
            if (hidePopupRunnable != null) handler.removeCallbacks(hidePopupRunnable);

            LinearLayout layout = new LinearLayout(ClipboardListenerService.this);
            layout.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setColor(Color.argb(240, 255, 255, 255));
            bg.setCornerRadius(30);
            bg.setStroke(2, Color.argb(100, 200, 200, 200));
            layout.setBackground(bg);
            layout.setAlpha(0.95f);
            layout.setPadding(25, 20, 25, 20);
            layout.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout wordRow = new LinearLayout(ClipboardListenerService.this);
            wordRow.setOrientation(LinearLayout.HORIZONTAL);
            wordRow.setGravity(Gravity.CENTER_VERTICAL);
            wordRow.setPadding(10, 0, 10, 0);

            TextView wordText = new TextView(ClipboardListenerService.this);
            wordText.setText(word);
            wordText.setTextSize(20);
            wordText.setTextColor(Color.BLACK);
            wordText.setPadding(0, 0, 30, 0);
            if (word.split("\\s+").length <= 4) { wordRow.addView(wordText); }

            Button speakBtn = new Button(ClipboardListenerService.this);
            speakBtn.setText("🔊");
            speakBtn.setTextSize(18);
            speakBtn.setBackgroundColor(Color.TRANSPARENT);
            speakBtn.setPadding(20, 0, 20, 0);
            speakBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (tts != null) {
                        tts.speak(word, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null);
                    }
                }
            });
            wordRow.addView(speakBtn);

            TextView meaningText = new TextView(ClipboardListenerService.this);
            meaningText.setText(wrapText(meaning, 7));
            meaningText.setTextSize(18);
            meaningText.setTextColor(Color.DKGRAY);
            meaningText.setPadding(0, 15, 0, 15);
            meaningText.setGravity(Gravity.CENTER_HORIZONTAL);

            layout.addView(wordRow);
            layout.addView(meaningText);
            int layoutType;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                layoutType = WindowManager.LayoutParams.TYPE_PHONE;
            }

            WindowManager.LayoutParams popupParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            popupParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            popupParams.y = 150;

            popupView = layout;
            windowManager.addView(popupView, popupParams);

            hidePopupRunnable = new Runnable() {
                @Override
                public void run() {
                    if (popupView != null) {
                        try { windowManager.removeView(popupView); } catch (Exception e) {}
                        popupView = null;
                    }
                }
            };
            int wordCount = meaning.split("\\s+").length;
            long displayTime = wordCount * 500L;
            if (displayTime < 3000) displayTime = 3000;
            if (displayTime > 15000) displayTime = 15000;
            handler.postDelayed(hidePopupRunnable, displayTime);
        }

        @Override
        public void onDestroy() {
            if (tts != null) { tts.stop(); tts.shutdown(); }
            super.onDestroy();
            if (clipboardRunnable != null) handler.removeCallbacks(clipboardRunnable);
            if (popupView != null) {
                try { windowManager.removeView(popupView); } catch (Exception e) {}
            }
        }

        @Override
        public IBinder onBind(Intent intent) { return null; }
    }
}
