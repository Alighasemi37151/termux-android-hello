package com.example.hello;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.widget.Toast;

public class MediaProjectionRequestActivity extends Activity {
    private static final int REQUEST_CODE = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // تأخیر کوچک برای آماده شدن Activity
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CODE);
            }
        }, 150);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            // ذخیره اجازه (نه خود MediaProjection)
            MainActivity.FloatingBubbleService.mediaProjectionResultCode = resultCode;
            MainActivity.FloatingBubbleService.mediaProjectionData = data;

            // شروع FloatingBubbleService
            Intent serviceIntent = new Intent(this, MainActivity.FloatingBubbleService.class);
            startService(serviceIntent);

            Toast.makeText(this, "حباب فعال شد", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "اجازه ضبط صفحه داده نشد", Toast.LENGTH_SHORT).show();
        }

        finish();
    }
}
