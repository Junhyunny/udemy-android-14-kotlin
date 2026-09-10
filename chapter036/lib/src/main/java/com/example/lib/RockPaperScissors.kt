package com.example.lib

fun main() {
    println("rock, paper or scissors? enter your choice!")
    val playerChoice = readln()
    val randomNumber = (1..3).random()
    val computerChoice = when (randomNumber) {
        1 -> "rock"
        2 -> "scissors"
        3 -> "paper"
        else -> "rock"
    }

    val winner = when (playerChoice) {
        computerChoice -> "Tie"
        "rock" if computerChoice == "scissors" -> "Player"
        "paper" if computerChoice == "rock" -> "Player"
        "scissors" if computerChoice == "paper" -> "Player"
        else -> "Computer"
    }

    if (winner == "Tie") {
        println("it is a Tie")
    } else {
        println("winner is $winner")
    }
}