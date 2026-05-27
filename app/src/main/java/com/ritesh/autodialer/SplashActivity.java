package com.ritesh.autodialer;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

public class SplashActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        TextView ritesh = findViewById(R.id.riteshText);
        TextView tagline = findViewById(R.id.taglineText);

        ritesh.setAlpha(0f);
        ritesh.setScaleX(0.45f);
        ritesh.setScaleY(0.45f);
        ritesh.setRotation(-8f);
        tagline.setAlpha(0f);
        tagline.setTranslationY(40f);

        ObjectAnimator fade = ObjectAnimator.ofFloat(ritesh, "alpha", 0f, 1f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(ritesh, "scaleX", 0.45f, 1.12f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(ritesh, "scaleY", 0.45f, 1.12f, 1f);
        ObjectAnimator rotate = ObjectAnimator.ofFloat(ritesh, "rotation", -8f, 4f, 0f);
        ObjectAnimator glowMove = ObjectAnimator.ofFloat(ritesh, "translationY", 24f, -8f, 0f);

        AnimatorSet titleSet = new AnimatorSet();
        titleSet.playTogether(fade, scaleX, scaleY, rotate, glowMove);
        titleSet.setDuration(1150);
        titleSet.setInterpolator(new AccelerateDecelerateInterpolator());
        titleSet.start();

        ObjectAnimator tagFade = ObjectAnimator.ofFloat(tagline, "alpha", 0f, 1f);
        ObjectAnimator tagMove = ObjectAnimator.ofFloat(tagline, "translationY", 40f, 0f);
        AnimatorSet tagSet = new AnimatorSet();
        tagSet.playTogether(tagFade, tagMove);
        tagSet.setStartDelay(700);
        tagSet.setDuration(700);
        tagSet.start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        }, 2200);
    }
}
