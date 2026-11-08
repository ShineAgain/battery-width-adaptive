package com.batteryadaptive.lsp;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

public class MainActivity extends Activity {

    private EditText etHeight;
    private EditText etWidth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences sp = PrefsUtil.prefs(this);
        etHeight = findViewById(R.id.et_height);
        etWidth = findViewById(R.id.et_width);
        etHeight.setText(String.valueOf(sp.getInt("hollow_height_dp", 17)));
        etWidth.setText(String.valueOf(sp.getInt("meter_width_dp", 0)));

        Button btnSave = findViewById(R.id.btn_save);
        btnSave.setOnClickListener(v -> PrefsUtil.saveConfig(
                this,
                PrefsUtil.parse(etHeight, 17),
                PrefsUtil.parse(etWidth, 0)));
    }
}
