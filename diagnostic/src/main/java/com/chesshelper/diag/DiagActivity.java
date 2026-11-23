package com.chesshelper.diag;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Minimal launch test — no helper code, no WebView, no engine. */
public class DiagActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("CHESS DIAG OK\nPackage: com.chesshelper.diag\nBuilt on GitHub");
        tv.setTextSize(24f);
        tv.setPadding(40, 120, 40, 40);
        setContentView(tv);
    }
}
