package com.ritesh.autodialer;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

public class CallActivity extends Activity {
    private TextView callNumber;
    private TextView nextNumber;
    private Button speakerButton;
    private Button muteButton;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            CallItem item = CallQueueManager.current();
            if (item == null) {
                callNumber.setText("No active queue item");
            } else {
                callNumber.setText(item.displayName() + "\n" + item.status + " | attempt " + item.attempts);
            }

            CallItem next = CallQueueManager.next();
            if (next == null) {
                nextNumber.setText("Next: none");
            } else {
                nextNumber.setText("Next: " + next.displayName());
            }

            speakerButton.setText(MyInCallService.isSpeakerEnabled() ? "Speaker: On" : "Speaker: Off");
            muteButton.setText(MyInCallService.isMuted() ? "Mute: On" : "Mute: Off");
            handler.postDelayed(this, 700);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        callNumber = findViewById(R.id.callNumber);
        nextNumber = findViewById(R.id.nextNumber);
        speakerButton = findViewById(R.id.speakerButton);
        muteButton = findViewById(R.id.muteButton);
        Button retryCallButton = findViewById(R.id.retryCallButton);
        Button skipCallButton = findViewById(R.id.skipCallButton);
        Button endCallButton = findViewById(R.id.endCallButton);

        retryCallButton.setOnClickListener(v -> {
            CallQueueManager.retry(this);
            refreshNow();
        });

        skipCallButton.setOnClickListener(v -> {
            CallQueueManager.skip(this);
            refreshNow();
        });

        speakerButton.setOnClickListener(v -> {
            boolean newValue = !MyInCallService.isSpeakerEnabled();
            MyInCallService.setSpeakerEnabled(newValue);
            refreshNow();
        });

        muteButton.setOnClickListener(v -> {
            boolean newValue = !MyInCallService.isMuted();
            MyInCallService.setMicrophoneMuted(newValue);
            refreshNow();
        });

        endCallButton.setOnClickListener(v -> {
            CallQueueManager.stop();
            MyInCallService.disconnectActiveCall();
            finish();
        });
    }

    private void refreshNow() {
        handler.removeCallbacks(refreshRunnable);
        handler.post(refreshRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refreshRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refreshRunnable);
    }
}
