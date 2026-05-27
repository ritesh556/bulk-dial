package com.ritesh.autodialer;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.TelecomManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int PICK_CSV_REQUEST = 2001;
    private static final int CALL_PERMISSION_REQUEST = 2002;
    private static final int DEFAULT_DIALER_REQUEST = 2003;

    private TextView defaultDialerStatus;
    private TextView currentNumber;
    private TextView statusText;
    private TextView progressText;
    private TextView listPreview;
    private CheckBox permissionCheck;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshUi();
            uiHandler.postDelayed(this, 800);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        defaultDialerStatus = findViewById(R.id.defaultDialerStatus);
        currentNumber = findViewById(R.id.currentNumber);
        statusText = findViewById(R.id.statusText);
        progressText = findViewById(R.id.progressText);
        listPreview = findViewById(R.id.listPreview);
        permissionCheck = findViewById(R.id.permissionCheck);

        Button defaultDialerButton = findViewById(R.id.defaultDialerButton);
        Button importCsvButton = findViewById(R.id.importCsvButton);
        Button startButton = findViewById(R.id.startButton);
        Button pauseButton = findViewById(R.id.pauseButton);
        Button resumeButton = findViewById(R.id.resumeButton);
        Button stopButton = findViewById(R.id.stopButton);
        Button retryButton = findViewById(R.id.retryButton);
        Button skipButton = findViewById(R.id.skipButton);
        Button sampleButton = findViewById(R.id.sampleButton);

        defaultDialerButton.setOnClickListener(v -> requestDefaultDialer());
        importCsvButton.setOnClickListener(v -> pickCsvFile());

        startButton.setOnClickListener(v -> {
            if (!permissionCheck.isChecked()) {
                toast("Please confirm you have permission to call these numbers.");
                return;
            }
            if (CallQueueManager.size() == 0) {
                toast("Import a CSV first.");
                return;
            }
            if (!isDefaultDialer()) {
                toast("Make this app the default dialer first so it can track call end.");
                requestDefaultDialer();
                return;
            }
            if (!hasCallPermission()) {
                requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, CALL_PERMISSION_REQUEST);
                return;
            }
            CallQueueManager.start(this);
            refreshUi();
        });

        pauseButton.setOnClickListener(v -> {
            CallQueueManager.pause();
            refreshUi();
        });

        resumeButton.setOnClickListener(v -> {
            if (!permissionCheck.isChecked()) {
                toast("Please confirm permission first.");
                return;
            }
            CallQueueManager.resume(this);
            refreshUi();
        });

        stopButton.setOnClickListener(v -> {
            CallQueueManager.stop();
            MyInCallService.disconnectActiveCall();
            refreshUi();
        });

        retryButton.setOnClickListener(v -> {
            if (!permissionCheck.isChecked()) {
                toast("Please confirm permission first.");
                return;
            }
            CallQueueManager.retry(this);
            refreshUi();
        });

        skipButton.setOnClickListener(v -> {
            CallQueueManager.skip(this);
            refreshUi();
        });

        sampleButton.setOnClickListener(v -> loadSampleData());

        handleDialIntent(getIntent());
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        uiHandler.post(refreshRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        uiHandler.removeCallbacks(refreshRunnable);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDialIntent(intent);
    }

    private void handleDialIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        String scheme = intent.getData().getScheme();
        if (!"tel".equalsIgnoreCase(scheme)) return;
        String phone = intent.getData().getSchemeSpecificPart();
        if (phone != null && !phone.trim().isEmpty()) {
            List<CallItem> one = new ArrayList<>();
            one.add(new CallItem("Dial intent", phone));
            CallQueueManager.load(one);
            permissionCheck.setChecked(true);
        }
    }

    private void pickCsvFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_CSV_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_CSV_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            try {
                getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            } catch (Exception ignored) {
                // Some file pickers do not support persistable permissions.
            }

            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                if (stream == null) {
                    toast("Could not open CSV file.");
                    return;
                }
                CsvUtils.CsvResult result = CsvUtils.parse(stream);
                CallQueueManager.load(result.items);
                toast("Loaded " + result.items.size() + " number(s). Skipped " + result.skippedRows + " row(s).");
            } catch (Exception ex) {
                toast("CSV error: " + ex.getMessage());
            }
            refreshUi();
        } else if (requestCode == DEFAULT_DIALER_REQUEST) {
            refreshUi();
        }
    }

    private void requestDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                    Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER);
                    startActivityForResult(intent, DEFAULT_DIALER_REQUEST);
                } else {
                    toast("This app is already the default dialer.");
                }
            } else {
                toast("Dialer role is not available on this device.");
            }
        } else {
            TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
            if (telecomManager != null && !getPackageName().equals(telecomManager.getDefaultDialerPackage())) {
                Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
                intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, getPackageName());
                startActivityForResult(intent, DEFAULT_DIALER_REQUEST);
            } else {
                toast("This app is already the default dialer.");
            }
        }
    }

    private boolean isDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            return roleManager != null && roleManager.isRoleHeld(RoleManager.ROLE_DIALER);
        }
        TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
        return telecomManager != null && getPackageName().equals(telecomManager.getDefaultDialerPackage());
    }

    private boolean hasCallPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CALL_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                CallQueueManager.start(this);
            } else {
                toast("Call permission denied.");
            }
        }
    }

    private void loadSampleData() {
        List<CallItem> items = new ArrayList<>();
        items.add(new CallItem("Demo One", "+15550101"));
        items.add(new CallItem("Demo Two", "+15550102"));
        items.add(new CallItem("Demo Three", "+15550103"));
        CallQueueManager.load(items);
        toast("Sample data loaded. Replace it with your real CSV before use.");
        refreshUi();
    }

    private void refreshUi() {
        defaultDialerStatus.setText(isDefaultDialer()
                ? "Default dialer: YES — call-end tracking enabled"
                : "Default dialer: NO — tap the button above before starting");

        CallItem item = CallQueueManager.current();
        if (item == null) {
            currentNumber.setText("Current: none");
        } else {
            currentNumber.setText("Current: " + item.displayName());
        }

        String runState;
        if (CallQueueManager.isPaused()) runState = "Paused";
        else if (CallQueueManager.isRunning()) runState = "Running";
        else runState = "Idle";

        statusText.setText("Status: " + CallQueueManager.getStatus() + "\nMode: " + runState);
        progressText.setText("Progress: " + CallQueueManager.currentIndexHuman() + " / " + CallQueueManager.size());
        listPreview.setText(CallQueueManager.preview());
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
