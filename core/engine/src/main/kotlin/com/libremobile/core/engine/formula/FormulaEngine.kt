package com.libremobile.core.engine.formula

import com.libremobile.core.model.calc.CalcWorkbook
import com.libremobile.core.model.calc.CellData
import com.libremobile.core.model.calc.CellPosition
import com.libremobile.core.model.calc.CellRange
import com.libremobile.core.model.calc.CellType
import com.libremobile.core.model.calc.Sheet
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

sealed class FormulaValue {
    data class NumberVal(val value: Double) : FormulaValue()
    data class StringVal(val value: String) : FormulaValue()
    data class BooleanVal(val value: Boolean) : FormulaValue()
    data class ErrorVal(val message: String) : FormulaValue()
    data class RangeVal(val values: List<FormulaValue>) : FormulaValue()

    fun asNumber(): Double? = when (this) {
        is NumberVal -> value
        is StringVal -> value.toDoubleOrNull()
        is BooleanVal -> if (value) 1.0 else 0.0
        else -> null
    }

    fun asString(): String = when (this) {
        is NumberVal -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        is StringVal -> value
        is BooleanVal -> if (value) "TRUE" else "FALSE"
        is ErrorVal -> message
        is RangeVal -> values.firstOrNull()?.asString() ?: ""
    }

    fun asBoolean(): Boolean = when (this) {
        is BooleanVal -> value
        is NumberVal -> value != 0.0
        is StringVal -> value.equals("TRUE", ignoreCase = true)
        else -> false
    }
}

class FormulaEngine(
    private val workbook: CalcWorkbook
) {
    private val evaluatingCells = mutableSetOf<CellPosition>()

    fun evaluateWorkbook(): CalcWorkbook {
        var currentWorkbook = workbook
        for (i in currentWorkbook.sheets.indices) {
            val sheet = currentWorkbook.sheets[i]
            val updatedCells = sheet.cells.toMutableMap()
            for ((pos, cell) in sheet.cells) {
                if (cell.isFormula) {
                    evaluatingCells.clear()
                    val result = evaluateFormula(cell.rawValue.removePrefix("="), sheet)
                    val evaluatedStr = result.asString()
                    val numVal = result.asNumber()
                    val cellType = when (result) {
                        is FormulaValue.NumberVal -> CellType.NUMBER
                        is FormulaValue.BooleanVal -> CellType.BOOLEAN
                        is FormulaValue.ErrorVal -> CellType.ERROR
                        else -> CellType.STRING
                    }
                    updatedCells[pos] = cell.copy(
                        evaluatedValue = evaluatedStr,
                        numericValue = numVal,
                        type = cellType
                    )
                } else {
                    // Check if numeric
                    val numVal = cell.rawValue.toDoubleOrNull()
                    val cellType = when {
                        numVal != null -> CellType.NUMBER
                        cell.rawValue.equals("TRUE", true) || cell.rawValue.equals("FALSE", true) -> CellType.BOOLEAN
                        cell.rawValue.isEmpty() -> CellType.EMPTY
                        else -> CellType.STRING
                    }
                    updatedCells[pos] = cell.copy(
                        numericValue = numVal,
                        type = cellType
                    )
                }
            }
            currentWorkbook = currentWorkbook.copy(
                sheets = currentWorkbook.sheets.toMutableList().also { it[i] = sheet.copy(cells = updatedCells) }
            )
        }
        return currentWorkbook
    }

    fun evaluateFormula(formula: String, currentSheet: Sheet): FormulaValue {
        return try {
            val tokens = tokenize(formula)
            val parser = FormulaParser(tokens, currentSheet, this)
            parser.parseExpression()
        } catch (e: Exception) {
            FormulaValue.ErrorVal("#VALUE!")
        }
    }

    fun resolveCell(pos: CellPosition, sheet: Sheet): FormulaValue {
        if (evaluatingCells.contains(pos)) {
            return FormulaValue.ErrorVal("#CIRCULAR!")
        }
        val cell = sheet.getCell(pos) ?: return FormulaValue.NumberVal(0.0)
        return if (cell.isFormula) {
            evaluatingCells.add(pos)
            val res = evaluateFormula(cell.rawValue.removePrefix("="), sheet)
            evaluatingCells.remove(pos)
            res
        } else {
            val num = cell.numericValue ?: cell.rawValue.toDoubleOrNull()
            if (num != null) {
                FormulaValue.NumberVal(num)
            } else if (cell.rawValue.equals("TRUE", true)) {
                FormulaValue.BooleanVal(true)
            } else if (cell.rawValue.equals("FALSE", true)) {
                FormulaValue.BooleanVal(false)
            } else {
                FormulaValue.StringVal(cell.rawValue)
            }
        }
    }

    fun resolveRange(range: CellRange, sheet: Sheet): List<FormulaValue> {
        val result = mutableListOf<FormulaValue>()
        range.forEachPosition { pos ->
            val v = resolveCell(pos, sheet)
            result.add(v)
        }
        return result
    }

    private fun tokenize(formula: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        val len = formula.length

        while (i < len) {
            val ch = formula[i]
            when {
                ch.isWhitespace() -> {
                    i++
                }
                ch == '"' -> {
                    sb.clear()
                    i++
                    while (i < len && formula[i] != '"') {
                        sb.append(formula[i])
                        i++
                    }
                    i++ // skip closing quote
                    tokens.add("\"${sb}\"")
                }
                ch in listOf('(', ')', ',', '+', '-', '*', '/', '^', '%', '&') -> {
                    tokens.add(ch.toString())
                    i++
                }
                ch in listOf('=', '<', '>') -> {
                    var op = ch.toString()
                    if (i + 1 < len) {
                        val next = formula[i + 1]
                        if ((ch == '<' && (next == '=' || next == '>')) || (ch == '>' && next == '=')) {
                            op += next
                            i++
                        }
                    }
                    tokens.add(op)
                    i++
                }
                ch.isLetterOrDigit() || ch == '.' || ch == '$' || ch == ':' || ch == '!' -> {
                    sb.clear()
                    while (i < len && (formula[i].isLetterOrDigit() || formula[i] == '.' || formula[i] == '$' || formula[i] == ':' || formula[i] == '!')) {
                        sb.append(formula[i])
                        i++
                    }
                    tokens.add(sb.toString())
                }
                else -> {
                    tokens.add(ch.toString())
                    i++
                }
            }
        }
        return tokens
    }
}

private class FormulaParser(
    private val tokens: List<String>,
    private val sheet: Sheet,
    private val engine: FormulaEngine
) {
    private var pos = 0

    private fun peek(): String? = tokens.getOrNull(pos)
    private fun next(): String = tokens[pos++]
    private fun hasMore(): Boolean = pos < tokens.size

    fun parseExpression(): FormulaValue {
        return parseComparison()
    }

    private fun parseComparison(): FormulaValue {
        var left = parseConcat()
        while (hasMore()) {
            val op = peek() ?: break
            if (op in listOf("=", "<>", "<", "<=", ">", ">=")) {
                next()
                val right = parseConcat()
                val leftNum = left.asNumber()
                val rightNum = right.asNumber()
                val cmp = if (leftNum != null && rightNum != null) {
                    leftNum.compareTo(rightNum)
                } else {
                    left.asString().compareTo(right.asString())
                }
                val boolRes = when (op) {
                    "=" -> cmp == 0
                    "<>" -> cmp != 0
                    "<" -> cmp < 0
                    "<=" -> cmp <= 0
                    ">" -> cmp > 0
                    ">=" -> cmp >= 0
                    else -> false
                }
                left = FormulaValue.BooleanVal(boolRes)
            } else break
        }
        return left
    }

    private fun parseConcat(): FormulaValue {
        var left = parseAddSub()
        while (hasMore() && peek() == "&") {
            next()
            val right = parseAddSub()
            left = FormulaValue.StringVal(left.asString() + right.asString())
        }
        return left
    }

    private fun parseAddSub(): FormulaValue {
        var left = parseMulDiv()
        while (hasMore()) {
            val op = peek() ?: break
            if (op == "+" || op == "-") {
                next()
                val right = parseMulDiv()
                val lNum = left.asNumber() ?: 0.0
                val rNum = right.asNumber() ?: 0.0
                left = FormulaValue.NumberVal(if (op == "+") lNum + rNum else lNum - rNum)
            } else break
        }
        return left
    }

    private fun parseMulDiv(): FormulaValue {
        var left = parseExponent()
        while (hasMore()) {
            val op = peek() ?: break
            if (op == "*" || op == "/") {
                next()
                val right = parseExponent()
                val lNum = left.asNumber() ?: 0.0
                val rNum = right.asNumber() ?: 0.0
                if (op == "/" && rNum == 0.0) {
                    return FormulaValue.ErrorVal("#DIV/0!")
                }
                left = FormulaValue.NumberVal(if (op == "*") lNum * rNum else lNum / rNum)
            } else break
        }
        return left
    }

    private fun parseExponent(): FormulaValue {
        var left = parseUnary()
        while (hasMore() && peek() == "^") {
            next()
            val right = parseUnary()
            val lNum = left.asNumber() ?: 0.0
            val rNum = right.asNumber() ?: 0.0
            left = FormulaValue.NumberVal(lNum.pow(rNum))
        }
        return left
    }

    private fun parseUnary(): FormulaValue {
        if (hasMore() && (peek() == "+" || peek() == "-")) {
            val op = next()
            val inner = parseUnary()
            val n = inner.asNumber() ?: 0.0
            return FormulaValue.NumberVal(if (op == "-") -n else n)
        }
        return parsePrimary()
    }

    private fun parsePrimary(): FormulaValue {
        if (!hasMore()) return FormulaValue.NumberVal(0.0)
        val token = next()

        // String literal
        if (token.startsWith("\"") && token.endsWith("\"")) {
            return FormulaValue.StringVal(token.substring(1, token.length - 1))
        }

        // Numeric literal
        val num = token.toDoubleOrNull()
        if (num != null) {
            return FormulaValue.NumberVal(num)
        }

        // Parentheses
        if (token == "(") {
            val expr = parseExpression()
            if (hasMore() && peek() == ")") next()
            return expr
        }

        // Boolean literal
        if (token.equals("TRUE", true)) return FormulaValue.BooleanVal(true)
        if (token.equals("FALSE", true)) return FormulaValue.BooleanVal(false)

        // Function call: e.g. SUM(A1:A5)
        if (hasMore() && peek() == "(") {
            next() // consume '('
            val args = mutableListOf<FormulaValue>()
            if (peek() != ")") {
                while (true) {
                    args.add(parseArgument())
                    if (hasMore() && peek() == ",") {
                        next()
                    } else break
                }
            }
            if (hasMore() && peek() == ")") next()
            return evaluateFunction(token.uppercase(Locale.ROOT), args)
        }

        // Cell Range: A1:B3
        if (token.contains(":")) {
            val range = CellRange.parse(token)
            if (range != null) {
                val vals = engine.resolveRange(range, sheet)
                return FormulaValue.RangeVal(vals)
            }
        }

        // Single Cell Reference: A1, $B$2
        val letters = token.replace("$", "").takeWhile { it.isLetter() }
        val digits = token.replace("$", "").dropWhile { it.isLetter() }
        if (letters.isNotEmpty() && digits.isNotEmpty() && digits.all { it.isDigit() }) {
            val pos = CellPosition.fromA1String(token)
            return engine.resolveCell(pos, sheet)
        }

        return FormulaValue.StringVal(token)
    }

    private fun parseArgument(): FormulaValue {
        if (hasMore()) {
            val p = peek() ?: ""
            if (p.contains(":")) {
                val range = CellRange.parse(p)
                if (range != null) {
                    next()
                    return FormulaValue.RangeVal(engine.resolveRange(range, sheet))
                }
            }
        }
        return parseExpression()
    }

    private fun flattenNumbers(args: List<FormulaValue>): List<Double> {
        val list = mutableListOf<Double>()
        for (arg in args) {
            when (arg) {
                is FormulaValue.NumberVal -> list.add(arg.value)
                is FormulaValue.RangeVal -> list.addAll(flattenNumbers(arg.values))
                is FormulaValue.StringVal -> arg.value.toDoubleOrNull()?.let { list.add(it) }
                else -> {}
            }
        }
        return list
    }

    private fun evaluateFunction(funcName: String, args: List<FormulaValue>): FormulaValue {
        return when (funcName) {
            "SUM" -> {
                val nums = flattenNumbers(args)
                FormulaValue.NumberVal(nums.sum())
            }
            "AVERAGE" -> {
                val nums = flattenNumbers(args)
                if (nums.isEmpty()) FormulaValue.ErrorVal("#DIV/0!")
                else FormulaValue.NumberVal(nums.average())
            }
            "MIN" -> {
                val nums = flattenNumbers(args)
                if (nums.isEmpty()) FormulaValue.NumberVal(0.0)
                else FormulaValue.NumberVal(nums.minOrNull() ?: 0.0)
            }
            "MAX" -> {
                val nums = flattenNumbers(args)
                if (nums.isEmpty()) FormulaValue.NumberVal(0.0)
                else FormulaValue.NumberVal(nums.maxOrNull() ?: 0.0)
            }
            "COUNT" -> {
                val nums = flattenNumbers(args)
                FormulaValue.NumberVal(nums.size.toDouble())
            }
            "COUNTA" -> {
                var count = 0
                fun countValues(list: List<FormulaValue>) {
                    for (v in list) {
                        when (v) {
                            is FormulaValue.RangeVal -> countValues(v.values)
                            is FormulaValue.StringVal -> if (v.value.isNotEmpty()) count++
                            else -> count++
                        }
                    }
                }
                countValues(args)
                FormulaValue.NumberVal(count.toDouble())
            }
            "IF" -> {
                if (args.isEmpty()) return FormulaValue.ErrorVal("#N/A")
                val condition = args[0].asBoolean()
                if (condition) {
                    args.getOrElse(1) { FormulaValue.BooleanVal(true) }
                } else {
                    args.getOrElse(2) { FormulaValue.BooleanVal(false) }
                }
            }
            "AND" -> {
                val result = args.all { it.asBoolean() }
                FormulaValue.BooleanVal(result)
            }
            "OR" -> {
                val result = args.any { it.asBoolean() }
                FormulaValue.BooleanVal(result)
            }
            "NOT" -> {
                val first = args.firstOrNull()?.asBoolean() ?: false
                FormulaValue.BooleanVal(!first)
            }
            "ABS" -> {
                val num = args.firstOrNull()?.asNumber() ?: 0.0
                FormulaValue.NumberVal(abs(num))
            }
            "ROUND" -> {
                val num = args.firstOrNull()?.asNumber() ?: 0.0
                val digits = args.getOrNull(1)?.asNumber()?.toInt() ?: 0
                val factor = 10.0.pow(digits)
                FormulaValue.NumberVal(round(num * factor) / factor)
            }
            "SQRT" -> {
                val num = args.firstOrNull()?.asNumber() ?: 0.0
                if (num < 0) FormulaValue.ErrorVal("#NUM!")
                else FormulaValue.NumberVal(sqrt(num))
            }
            "CONCAT", "CONCATENATE" -> {
                val sb = StringBuilder()
                fun appendVals(list: List<FormulaValue>) {
                    for (v in list) {
                        if (v is FormulaValue.RangeVal) appendVals(v.values)
                        else sb.append(v.asString())
                    }
                }
                appendVals(args)
                FormulaValue.StringVal(sb.toString())
            }
            "UPPER" -> FormulaValue.StringVal(args.firstOrNull()?.asString()?.uppercase(Locale.ROOT) ?: "")
            "LOWER" -> FormulaValue.StringVal(args.firstOrNull()?.asString()?.lowercase(Locale.ROOT) ?: "")
            "TRIM" -> FormulaValue.StringVal(args.firstOrNull()?.asString()?.trim() ?: "")
            "LEN" -> FormulaValue.NumberVal((args.firstOrNull()?.asString()?.length ?: 0).toDouble())
            "LEFT" -> {
                val str = args.firstOrNull()?.asString() ?: ""
                val n = args.getOrNull(1)?.asNumber()?.toInt() ?: 1
                FormulaValue.StringVal(str.take(n))
            }
            "RIGHT" -> {
                val str = args.firstOrNull()?.asString() ?: ""
                val n = args.getOrNull(1)?.asNumber()?.toInt() ?: 1
                FormulaValue.StringVal(str.takeLast(n))
            }
            "VLOOKUP" -> {
                // VLOOKUP(lookup_value, table_array, col_index, [range_lookup])
                if (args.size < 3) return FormulaValue.ErrorVal("#N/A")
                val lookupKey = args[0].asString()
                val colIndex = (args[2].asNumber()?.toInt() ?: 1) - 1
                // Table array is passed as RangeVal
                val rangeVal = args[1] as? FormulaValue.RangeVal ?: return FormulaValue.ErrorVal("#VALUE!")
                // Match exact or approx
                var match: FormulaValue? = null
                for (rowItem in rangeVal.values) {
                    if (rowItem.asString().equals(lookupKey, ignoreCase = true)) {
                        match = rowItem
                        break
                    }
                }
                match ?: FormulaValue.ErrorVal("#N/A")
            }
            else -> FormulaValue.ErrorVal("#NAME?")
        }
    }
}
