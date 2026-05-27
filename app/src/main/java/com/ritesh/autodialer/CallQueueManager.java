package com.ritesh.autodialer;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.TelecomManager;

import java.util.ArrayList;
import java.util.List;

public class CallQueueManager {
    private static final List<CallItem> queue = new ArrayList<>();
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final long NEXT_CALL_DELAY_MS = 3000;
    private static final int MAX_ATTEMPTS = 3;

    private static int currentIndex = 0;
    private static boolean running = false;
    private static boolean paused = false;
    private static String status = "Waiting for CSV";

    public static synchronized void load(List<CallItem> items) {
        queue.clear();
        queue.addAll(items);
        currentIndex = 0;
        running = false;
        paused = false;
        status = "Loaded " + items.size() + " callable number(s)";
    }

    public static synchronized List<CallItem> snapshot() {
        return new ArrayList<>(queue);
    }

    public static synchronized int size() {
        return queue.size();
    }

    public static synchronized int currentIndexHuman() {
        if (queue.isEmpty()) return 0;
        return Math.min(currentIndex + 1, queue.size());
    }

    public static synchronized CallItem current() {
        if (currentIndex < 0 || currentIndex >= queue.size()) return null;
        return queue.get(currentIndex);
    }

    public static synchronized CallItem next() {
        if (queue.isEmpty()) return null;
        for (int i = currentIndex + 1; i < queue.size(); i++) {
            CallItem item = queue.get(i);
            if (item.status == CallStatus.PENDING || item.status == CallStatus.FAILED) {
                return item;
            }
        }
        return null;
    }

    public static synchronized String getStatus() {
        return status;
    }

    public static synchronized boolean isRunning() {
        return running;
    }

    public static synchronized boolean isPaused() {
        return paused;
    }

    public static void start(Context context) {
        synchronized (CallQueueManager.class) {
            if (queue.isEmpty()) {
                status = "No numbers loaded";
                return;
            }
            running = true;
            paused = false;
            status = "Starting call queue";
        }
        dialCurrent(context.getApplicationContext());
    }

    public static synchronized void stop() {
        running = false;
        paused = false;
        handler.removeCallbacksAndMessages(null);
        status = "Stopped";
    }

    public static synchronized void pause() {
        if (!running) {
            status = "Not running";
            return;
        }
        paused = true;
        handler.removeCallbacksAndMessages(null);
        status = "Paused";
    }

    public static void resume(Context context) {
        synchronized (CallQueueManager.class) {
            if (!running) {
                status = "Not running";
                return;
            }
            paused = false;
            status = "Resuming";
        }
        dialCurrent(context.getApplicationContext());
    }

    public static void retry(Context context) {
        synchronized (CallQueueManager.class) {
            CallItem item = current();
            if (item == null) {
                status = "Nothing to retry";
                return;
            }
            handler.removeCallbacksAndMessages(null);
            item.status = CallStatus.PENDING;
            item.note = "Retry requested by user";
            running = true;
            paused = false;
            status = "Retrying " + item.phone;
        }

        boolean hadActiveCall = MyInCallService.disconnectActiveCall();
        if (!hadActiveCall) {
            dialCurrent(context.getApplicationContext());
        }
    }

    public static void skip(Context context) {
        synchronized (CallQueueManager.class) {
            CallItem item = current();
            if (item == null) {
                status = "Nothing to skip";
                return;
            }
            item.status = CallStatus.SKIPPED;
            item.note = "Skipped by user";
            currentIndex++;
            status = "Skipped; moving to next number";
        }

        boolean hadActiveCall = MyInCallService.disconnectActiveCall();
        if (!hadActiveCall) {
            dialCurrent(context.getApplicationContext());
        }
    }

    public static void onCallEnded(Context context) {
        synchronized (CallQueueManager.class) {
            CallItem item = current();
            if (item != null && item.status == CallStatus.CALLING) {
                item.status = CallStatus.DONE;
                item.note = "Call ended";
                currentIndex++;
            }

            if (!running || paused) {
                status = paused ? "Paused after call" : "Call ended; queue stopped";
                return;
            }

            if (currentIndex >= queue.size()) {
                running = false;
                status = "Finished all numbers";
                return;
            }

            status = "Next call starts in 3 seconds";
        }

        handler.postDelayed(() -> dialCurrent(context.getApplicationContext()), NEXT_CALL_DELAY_MS);
    }

    private static void dialCurrent(Context context) {
        CallItem item;
        synchronized (CallQueueManager.class) {
            handler.removeCallbacksAndMessages(null);

            if (!running || paused) return;

            while (currentIndex < queue.size()) {
                CallItem candidate = queue.get(currentIndex);
                if (candidate.status == CallStatus.PENDING || candidate.status == CallStatus.FAILED) break;
                currentIndex++;
            }

            if (currentIndex >= queue.size()) {
                running = false;
                status = "Finished all numbers";
                return;
            }

            item = queue.get(currentIndex);
            if (isBlockedNumber(item.phone)) {
                item.status = CallStatus.SKIPPED;
                item.note = "Emergency or blocked number skipped";
                currentIndex++;
                status = "Skipped blocked number";
                handler.postDelayed(() -> dialCurrent(context), 500);
                return;
            }

            if (item.attempts >= MAX_ATTEMPTS) {
                item.status = CallStatus.FAILED;
                item.note = "Max retry limit reached";
                currentIndex++;
                status = "Max retry reached; moving on";
                handler.postDelayed(() -> dialCurrent(context), 500);
                return;
            }

            item.status = CallStatus.CALLING;
            item.attempts++;
            item.note = "Dialing attempt " + item.attempts;
            status = "Dialing " + item.phone;
        }

        Uri uri = Uri.parse("tel:" + Uri.encode(item.phone));
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    synchronized (CallQueueManager.class) {
                        item.status = CallStatus.FAILED;
                        item.note = "CALL_PHONE permission missing";
                        status = "CALL_PHONE permission missing";
                    }
                    return;
                }
                TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
                if (telecomManager != null) {
                    telecomManager.placeCall(uri, new Bundle());
                    openCallScreen(context);
                    return;
                }
            }

            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            openCallScreen(context);
        } catch (Exception ex) {
            synchronized (CallQueueManager.class) {
                item.status = CallStatus.FAILED;
                item.note = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                status = "Call failed: " + ex.getClass().getSimpleName();
            }
        }
    }

    private static void openCallScreen(Context context) {
        Intent screen = new Intent(context, CallActivity.class);
        screen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(screen);
    }

    private static boolean isBlockedNumber(String phone) {
        if (phone == null) return true;
        String digits = phone.replaceAll("[^0-9]", "");
        return digits.equals("911") || digits.equals("112") || digits.equals("999") ||
                digits.equals("100") || digits.equals("101") || digits.equals("102");
    }

    public static synchronized String preview() {
        if (queue.isEmpty()) return "Queue preview will appear here.";
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(queue.size(), 40);
        for (int i = 0; i < limit; i++) {
            CallItem item = queue.get(i);
            sb.append(i + 1)
                    .append(". ")
                    .append(item.displayName())
                    .append("  [")
                    .append(item.status)
                    .append("] attempts=")
                    .append(item.attempts);
            if (!item.note.isEmpty()) sb.append(" | ").append(item.note);
            sb.append('\n');
        }
        if (queue.size() > limit) sb.append("...and ").append(queue.size() - limit).append(" more");
        return sb.toString();
    }
}
