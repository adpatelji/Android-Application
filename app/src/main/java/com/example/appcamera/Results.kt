package com.example.appcamera

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView

class Results : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)

        val intent = intent
        val hsStr  = intent.getIntExtra("humanScore", 0 )
        val csStr = intent.getIntExtra("computerScore", 0 )

        val hs  = if(hsStr == -1) 0
                  else hsStr
        val cs =  if(csStr == -1) 0
                  else csStr

        val results =
            when {
                hs > cs -> " Human wins by ${hs - cs} runs"
                cs > hs -> " Computer wins by 1 wicket"
                else -> "Tie"
            }

        val textView = findViewById<TextView>(R.id.Results)
        textView.text = results

        val textView1 = findViewById<TextView>(R.id.textViewComputerScore)
        textView1.text = "Computer: $cs runs"

        val textView2 = findViewById<TextView>(R.id.textViewHumanScore)
        textView2.text = "Human: $hs runs"

    }
}