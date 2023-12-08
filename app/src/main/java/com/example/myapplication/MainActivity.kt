package com.example.myapplication

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<ImageView>(R.id.iv_test_main).setImageResource(R.drawable.tiger_test_main)
        findViewById<ImageView>(R.id.iv_test_library).setImageResource(com.example.testlibrary.R.drawable.horse_test_library)
        findViewById<ImageView>(R.id.iv_test_aar).setImageResource(com.example.testaar.R.drawable.horse_test_aar)
        resources.getString(R.string.test_string)
    }
}