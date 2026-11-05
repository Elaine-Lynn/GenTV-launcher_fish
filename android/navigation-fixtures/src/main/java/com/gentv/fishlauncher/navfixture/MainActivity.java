package com.gentv.fishlauncher.navfixture;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.TextView;
public final class MainActivity extends Activity {
    public void onCreate(Bundle state){super.onCreate(state);TextView label=new TextView(this);label.setText(getApplicationInfo().loadLabel(getPackageManager())+"\n遥控器导航验证\n按返回键回到GenTV");label.setTextSize(30);label.setGravity(Gravity.CENTER);label.setTextColor(Color.WHITE);label.setBackgroundColor(0xff24483f);setContentView(label);}
}
