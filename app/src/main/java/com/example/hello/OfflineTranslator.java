package com.example.hello;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.Set;

/**
 * ترجمه آفلاین با ML Kit Translate
 * توجه: نیازمند Google Play Services
 */
public class OfflineTranslator {

    private static final String TAG = "OfflineTranslator";

    public interface Callback {
        void onSuccess(String translated);
        void onFailure(String error);
    }

    public interface ModelStatusCallback {
        void onStatus(boolean isDownloaded);
    }

    public interface DownloadCallback {
        void onProgress(int percent);
        void onSuccess();
        void onFailure(String error);
    }

    public interface ListCallback {
        void onList(Set<TranslateRemoteModel> models);
    }

    // ═══ آیا GMS نصب هست؟ ═══
    public static boolean isGmsAvailable(Context context) {
        try {
            GoogleApiAvailability api = GoogleApiAvailability.getInstance();
            int status = api.isGooglePlayServicesAvailable(context);
            return status == ConnectionResult.SUCCESS;
        } catch (Exception e) {
            Log.e(TAG, "GMS check failed: " + e.getMessage());
            return false;
        }
    }

    // ═══ تبدیل کد زبان به ML Kit ═══
    private static String toMlkitLang(String code) {
        switch (code) {
            case "en": return TranslateLanguage.ENGLISH;
            case "fa": return TranslateLanguage.PERSIAN;
            case "ar": return TranslateLanguage.ARABIC;
            case "tr": return TranslateLanguage.TURKISH;
            case "fr": return TranslateLanguage.FRENCH;
            case "de": return TranslateLanguage.GERMAN;
            case "es": return TranslateLanguage.SPANISH;
            case "ru": return TranslateLanguage.RUSSIAN;
            case "zh": return TranslateLanguage.CHINESE;
            case "ja": return TranslateLanguage.JAPANESE;
            case "ko": return TranslateLanguage.KOREAN;
            case "hi": return TranslateLanguage.HINDI;
            case "it": return TranslateLanguage.ITALIAN;
            case "pt": return TranslateLanguage.PORTUGUESE;
            default: return null;
        }
    }

    // ═══ بررسی نصب بودن مدل ═══
    public static void isModelDownloaded(Context context, String src, String tgt,
                                          final ModelStatusCallback cb) {
        if (!isGmsAvailable(context)) {
            cb.onStatus(false);
            return;
        }
        String mlSrc = toMlkitLang(src);
        String mlTgt = toMlkitLang(tgt);
        if (mlSrc == null || mlTgt == null) {
            cb.onStatus(false);
            return;
        }

        RemoteModelManager manager = RemoteModelManager.getInstance();
        TranslateRemoteModel model = new TranslateRemoteModel.Builder(mlTgt).build();

        manager.isModelDownloaded(model)
            .addOnSuccessListener(downloaded -> cb.onStatus(downloaded))
            .addOnFailureListener(e -> {
                Log.e(TAG, "isModelDownloaded: " + e.getMessage());
                cb.onStatus(false);
            });
    }

    // ═══ دانلود مدل ═══
    public static void downloadModel(Context context, String src, String tgt,
                                      final DownloadCallback cb) {
        if (!isGmsAvailable(context)) {
            cb.onFailure("Google Play Services نصب نیست");
            return;
        }
        String mlSrc = toMlkitLang(src);
        String mlTgt = toMlkitLang(tgt);
        if (mlSrc == null || mlTgt == null) {
            cb.onFailure("زبان پشتیبانی نمی‌شه");
            return;
        }

        TranslatorOptions options = new TranslatorOptions.Builder()
            .setSourceLanguage(mlSrc)
            .setTargetLanguage(mlTgt)
            .build();

        final Translator translator = Translation.getClient(options);
        DownloadConditions conditions = new DownloadConditions.Builder()
            .build();

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener(unused -> {
                Log.d(TAG, "Model downloaded: " + src + "->" + tgt);
                cb.onSuccess();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Download failed: " + e.getMessage());
                cb.onFailure(e.getMessage());
            });
    }

    // ═══ ترجمه آفلاین ═══
    public static void translate(Context context, String text, String src, String tgt,
                                  final Callback cb) {
        if (!isGmsAvailable(context)) {
            cb.onFailure("GMS نصب نیست");
            return;
        }
        if (text == null || text.trim().isEmpty()) {
            cb.onFailure("متن خالی");
            return;
        }

        String mlSrc = toMlkitLang(src);
        String mlTgt = toMlkitLang(tgt);
        if (mlSrc == null || mlTgt == null) {
            cb.onFailure("زبان پشتیبانی نمی‌شه");
            return;
        }

        TranslatorOptions options = new TranslatorOptions.Builder()
            .setSourceLanguage(mlSrc)
            .setTargetLanguage(mlTgt)
            .build();

        final Translator translator = Translation.getClient(options);
        DownloadConditions conditions = new DownloadConditions.Builder()
            .build();

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener(unused -> {
                translator.translate(text)
                    .addOnSuccessListener(result -> {
                        Log.d(TAG, "Translated offline: " + result);
                        cb.onSuccess(result);
                    })
                    .addOnFailureListener(e -> cb.onFailure(e.getMessage()));
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Need download first: " + e.getMessage());
                cb.onFailure("مدل دانلود نشده");
            });
    }

    // ═══ لیست مدل‌های دانلود شده ═══
    public static void getDownloadedModels(Context context, final ListCallback cb) {
        if (!isGmsAvailable(context)) {
            cb.onList(null);
            return;
        }
        RemoteModelManager manager = RemoteModelManager.getInstance();
        manager.getDownloadedModels(TranslateRemoteModel.class)
            .addOnSuccessListener(models -> cb.onList(models))
            .addOnFailureListener(e -> {
                Log.e(TAG, "getDownloadedModels: " + e.getMessage());
                cb.onList(null);
            });
    }

    // ═══ حذف مدل ═══
    public static void deleteModel(Context context, String tgt,
                                    final ModelStatusCallback cb) {
        if (!isGmsAvailable(context)) {
            cb.onStatus(false);
            return;
        }
        String mlTgt = toMlkitLang(tgt);
        if (mlTgt == null) {
            cb.onStatus(false);
            return;
        }
        RemoteModelManager manager = RemoteModelManager.getInstance();
        TranslateRemoteModel model = new TranslateRemoteModel.Builder(mlTgt).build();

        manager.deleteDownloadedModel(model)
            .addOnSuccessListener(unused -> cb.onStatus(true))
            .addOnFailureListener(e -> cb.onStatus(false));
    }
}
