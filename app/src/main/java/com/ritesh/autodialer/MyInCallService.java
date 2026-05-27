package com.ritesh.autodialer;

import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;

public class MyInCallService extends InCallService {
    private static Call activeCall;
    private static MyInCallService serviceInstance;
    private static boolean speakerEnabled = false;
    private static boolean microphoneMuted = false;

    @Override
    public void onCreate() {
        super.onCreate();
        serviceInstance = this;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (serviceInstance == this) serviceInstance = null;
    }

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        activeCall = call;
        applySpeakerRoute();
        applyMuteState();

        call.registerCallback(new Call.Callback() {
            @Override
            public void onStateChanged(Call call, int state) {
                super.onStateChanged(call, state);
                if (state == Call.STATE_DISCONNECTED) {
                    if (activeCall == call) activeCall = null;
                    CallQueueManager.onCallEnded(getApplicationContext());
                }
            }
        });
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        if (activeCall == call) activeCall = null;
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState audioState) {
        super.onCallAudioStateChanged(audioState);
        if (audioState != null) {
            microphoneMuted = audioState.isMuted();
        }
    }

    public static boolean disconnectActiveCall() {
        if (activeCall != null) {
            activeCall.disconnect();
            return true;
        }
        return false;
    }

    public static boolean hasActiveCall() {
        return activeCall != null;
    }

    public static boolean isSpeakerEnabled() {
        return speakerEnabled;
    }

    public static boolean isMuted() {
        return microphoneMuted;
    }

    public static void setMicrophoneMuted(boolean enabled) {
        microphoneMuted = enabled;
        if (serviceInstance != null) {
            serviceInstance.applyMuteState();
        }
    }

    public static void setSpeakerEnabled(boolean enabled) {
        speakerEnabled = enabled;
        if (serviceInstance != null) {
            serviceInstance.applySpeakerRoute();
        }
    }

    private void applySpeakerRoute() {
        if (speakerEnabled) {
            setAudioRoute(CallAudioState.ROUTE_SPEAKER);
        } else {
            setAudioRoute(CallAudioState.ROUTE_EARPIECE);
        }
    }

    private void applyMuteState() {
        setMuted(microphoneMuted);
    }
}
