package com.example.hello;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class MyDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME = "dictionary.db";
    private static final int DB_VERSION = 1;

    public MyDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE dictionary (english TEXT, persian TEXT)");
        insertWord(db, "hello", "سلام");
        insertWord(db, "book", "کتاب");
        insertWord(db, "water", "آب");
        insertWord(db, "car", "ماشین");
        insertWord(db, "house", "خانه");
        insertWord(db, "friend", "دوست");
        insertWord(db, "love", "عشق");
        insertWord(db, "food", "غذا");
        insertWord(db, "time", "زمان");
        insertWord(db, "work", "کار");
    }

    private void insertWord(SQLiteDatabase db, String eng, String per) {
        ContentValues values = new ContentValues();
        values.put("english", eng);
        values.put("persian", per);
        db.insert("dictionary", null, values);
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
        return "پیدا نشد!";
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS dictionary");
        onCreate(db);
    }
}
