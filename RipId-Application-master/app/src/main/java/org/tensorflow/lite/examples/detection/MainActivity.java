package org.tensorflow.lite.examples.detection;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
    public void show_ripen_activity(View view){
        Intent intent = new Intent(MainActivity.this, Banana.class);
        startActivity(intent);
    }
    public void show_object_identify(View view){
        Intent intent = new Intent(MainActivity.this, DetectorActivity.class);
        startActivity(intent);
    }
}