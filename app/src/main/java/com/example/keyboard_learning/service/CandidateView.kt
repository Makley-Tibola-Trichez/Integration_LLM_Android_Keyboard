package com.example.keyboard_learning.service

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.keyboard_learning.R

class CandidateView(context: Context) : LinearLayout(context) {

    var service: MyInputMethod? = null

    private var firstPrediction: TextView
    private var secondPrediction: TextView
    private var thirdPrediction: TextView
    private var viewBind: View

    init {
        viewBind = View.inflate(context, R.layout.candidate_layout, this)

        firstPrediction = viewBind.findViewById(R.id.first_prediction)
        firstPrediction.setOnClickListener {
            v -> service?.pickSuggestion((v as TextView).text.toString())
        }

        secondPrediction = viewBind.findViewById(R.id.second_prediction)
        secondPrediction.setOnClickListener {
            v -> service?.pickSuggestion((v as TextView).text.toString())
        }

        thirdPrediction = viewBind.findViewById(R.id.third_prediction)
        thirdPrediction.setOnClickListener {
            v -> service?.pickSuggestion((v as TextView).text.toString())
        }
    }


     fun updatePredictions(prediction: List<String> = listOf()) {

         val mutablePredictions = prediction.toMutableList()

         if (mutablePredictions.size < 3) {

             val elementsToAdd = 3 - mutablePredictions.size

             for (i in 0 until elementsToAdd) {
                 mutablePredictions.add("")
             }
         }

        firstPrediction.text =  mutablePredictions[0]
        secondPrediction.text =  mutablePredictions[1]
        thirdPrediction.text =  mutablePredictions[2]
    }
}