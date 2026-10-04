package com.whatsmyphonenumber;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQUEST_PERMISSIONS = 1;

    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        list.setPadding(pad, pad, pad, pad);
        scroll.addView(list);

        // targetSdk 35 draws edge-to-edge, so keep content clear of the system bars.
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        render();
    }

    private void render() {
        list.removeAllViews();
        addText(getString(R.string.title), 26, true);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            addText(getString(R.string.no_telephony), 16, false);
            return;
        }

        if (!hasPermissions()) {
            addText(getString(R.string.need_permission), 16, false);
            addButton(getString(R.string.grant), v -> requestPermissions());
            return;
        }

        List<SimNumber> sims = readNumbers();
        if (sims.isEmpty()) {
            addText(getString(R.string.no_sim), 16, false);
        } else {
            boolean anyMissing = false;
            for (SimNumber sim : sims) {
                addSim(sim);
                if (sim.number == null) anyMissing = true;
            }
            addText(getString(R.string.tap_to_copy), 14, false).setAlpha(0.6f);
            if (anyMissing) addText(getString(R.string.unknown_hint), 14, false).setAlpha(0.6f);
        }
        addButton(getString(R.string.refresh), v -> render());
    }

    private String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS};
        }
        return new String[]{Manifest.permission.READ_PHONE_STATE};
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        for (String p : requiredPermissions()) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(requiredPermissions(), REQUEST_PERMISSIONS);
        }
    }

    /** Returns one entry per active SIM; {@code number} is null when the SIM doesn't expose it. */
    @SuppressWarnings({"MissingPermission", "deprecation"})
    private List<SimNumber> readNumbers() {
        List<SimNumber> result = new ArrayList<>();
        SubscriptionManager sm = (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        List<SubscriptionInfo> subs = null;
        try {
            subs = sm != null ? sm.getActiveSubscriptionInfoList() : null;
        } catch (SecurityException ignored) {
        }

        if (subs != null) {
            for (SubscriptionInfo info : subs) {
                int subId = info.getSubscriptionId();
                String number = null;
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        number = sm.getPhoneNumber(subId);
                    }
                    if (TextUtils.isEmpty(number)) number = info.getNumber();
                    if (TextUtils.isEmpty(number) && tm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        number = tm.createForSubscriptionId(subId).getLine1Number();
                    }
                } catch (SecurityException ignored) {
                }
                CharSequence carrier = info.getCarrierName();
                if (TextUtils.isEmpty(carrier)) carrier = info.getDisplayName();
                result.add(new SimNumber(info.getSimSlotIndex() + 1,
                        carrier != null ? carrier.toString() : "", clean(number)));
            }
        } else if (tm != null && tm.getSimState() == TelephonyManager.SIM_STATE_READY) {
            String number = null;
            try {
                number = tm.getLine1Number();
            } catch (SecurityException ignored) {
            }
            result.add(new SimNumber(1, tm.getSimOperatorName(), clean(number)));
        }
        return result;
    }

    private static String clean(String number) {
        return TextUtils.isEmpty(number) ? null : number.trim();
    }

    private void addSim(SimNumber sim) {
        TextView label = addText(getString(R.string.sim_label, sim.slot, sim.carrier), 14, false);
        label.setAlpha(0.7f);
        label.setPadding(0, dp(20), 0, 0);

        if (sim.number == null) {
            addText(getString(R.string.unknown_number), 18, false);
            return;
        }
        String pretty = PhoneNumberUtils.formatNumber(sim.number, Locale.getDefault().getCountry());
        TextView num = addText(pretty != null ? pretty : sim.number, 30, true);
        num.setTextIsSelectable(false);
        num.setOnClickListener(v -> copy(sim.number));
    }

    private void copy(String number) {
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cb.setPrimaryClip(ClipData.newPlainText("Phone number", number));
        Toast.makeText(this, getString(R.string.copied, number), Toast.LENGTH_SHORT).show();
    }

    private TextView addText(String text, int sp, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        tv.setPadding(0, dp(6), 0, dp(6));
        list.addView(tv);
        return tv;
    }

    private void addButton(String text, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(24);
        lp.gravity = Gravity.START;
        list.addView(b, lp);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class SimNumber {
        final int slot;
        final String carrier;
        final String number;

        SimNumber(int slot, String carrier, String number) {
            this.slot = slot;
            this.carrier = carrier;
            this.number = number;
        }
    }
}
