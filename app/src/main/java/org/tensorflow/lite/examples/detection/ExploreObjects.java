package org.tensorflow.lite.examples.detection;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ExploreObjects extends AppCompatActivity {
    private static final String DATA_FILE = "data_file.txt";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explore_objects);
        Map<String, String> map = new HashMap<String, String>();
        String st;
        try {
            BufferedReader bufferedReader = new BufferedReader(new
                    InputStreamReader(getAssets().open("data_file.txt")));

            while ((st = bufferedReader.readLine()) != null) {
                String[] parts = st.split(":");
                map.put(parts[0], parts[1]);
                Toast.makeText(ExploreObjects.this,parts[0]+":"+ parts[1], Toast.LENGTH_LONG).show();
            }
        } catch(Exception e){
            Toast.makeText(ExploreObjects.this, e.toString(), Toast.LENGTH_LONG).show();
        }

        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.mobile_list);

        try {
            Intent intent = getIntent();
            ArrayList objList = (ArrayList) intent.getSerializableExtra("ObjList");
            int i=0;
            for (Object objTitle : objList) {
                String Str = objTitle.toString();
                TextView textView = new TextView(this);
                textView.setText(Str);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                textView.setTypeface(null, Typeface.BOLD);
                textView.setTextColor(Color.MAGENTA);
                linearLayout.addView(textView);



                TextView textView2 = new TextView(this);
                textView2.setText(map.get(Str));
                textView.setTextColor(Color.GRAY);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                linearLayout.addView(textView2);

                TextView textView21 = new TextView(this);
                textView21.setText("-----------------------------------------------------------------------------");
                textView.setTextColor(Color.GRAY);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                linearLayout.addView(textView21);
            }




        } catch(Exception e){
            Toast.makeText(ExploreObjects.this, e.toString(), Toast.LENGTH_LONG).show();
        }
    }
}