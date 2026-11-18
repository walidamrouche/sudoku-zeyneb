package com.zeyneb.sudoku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.zeyneb.sudoku.ui.App

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App(vm) }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground(true)
    }

    override fun onStop() {
        vm.onForeground(false)
        super.onStop()
    }
}
