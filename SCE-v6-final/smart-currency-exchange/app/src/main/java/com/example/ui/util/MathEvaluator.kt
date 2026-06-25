package com.example.ui.util

import kotlin.math.*

object MathEvaluator {
    fun evaluate(expression: String): Double {
        val sanitized = expression.replace(" ", "")
            .replace(",", "")
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
        return parseExpression(sanitized)
    }

    private fun parseExpression(str: String): Double {
        return object : Any() {
            var pos = -1
            var ch = 0

            fun nextChar() {
                ch = if (++pos < str.length) str[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseTerm()
                if (pos < str.length) throw IllegalArgumentException("Unexpected character: " + ch.toChar())
                return x
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    if (eat('+'.code)) x += parseFactor() // addition
                    else if (eat('-'.code)) x -= parseFactor() // subtraction
                    else return x
                }
            }

            fun parseFactor(): Double {
                var x = parseHighLevel()
                while (true) {
                    if (eat('*'.code)) x *= parseHighLevel() // multiplication
                    else if (eat('/'.code)) {
                        val divisor = parseHighLevel()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x /= divisor // division
                    } else return x
                }
            }

            fun parseHighLevel(): Double {
                if (eat('+'.code)) return parseHighLevel() // unary plus
                if (eat('-'.code)) return -parseHighLevel() // unary minus

                var x: Double
                val startPos = this.pos
                if (eat('('.code)) { // parentheses
                    x = parseTerm()
                    eat(')'.code)
                } else if ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) { // numbers
                    while ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) nextChar()
                    x = str.substring(startPos, this.pos).toDouble()
                } else {
                    throw IllegalArgumentException("Unexpected character: " + ch.toChar())
                }

                return x
            }
        }.parse()
    }
}
