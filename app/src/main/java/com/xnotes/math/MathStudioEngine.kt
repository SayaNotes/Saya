package com.xnotes.math

import kotlin.math.*

/** On-device numerical engine used by Math Studio. */
object MathStudioEngine {
    data class Point(val x: Double, val y: Double)
    fun evaluate(expression: String, x: Double = 0.0): Double =
        Parser(expression, x).parse().also { require(it.isFinite()) { "Result is not finite." } }

    fun solveEquation(equation: String): List<Double> {
        val sides=equation.split('=')
        require(sides.size==2 && sides.all { it.isNotBlank() }) { "Enter an equation such as x^2 - 4 = 0." }
        fun f(x:Double)=evaluate(sides[0],x)-evaluate(sides[1],x)
        val c = runCatching { f(0.0) }.getOrNull()
        val p = runCatching { f(1.0) }.getOrNull()
        val m = runCatching { f(-1.0) }.getOrNull()
        val atTwo = runCatching { f(2.0) }.getOrNull()
        if (c != null && p != null && m != null && atTwo != null) {
            val a = (p + m - 2.0 * c) / 2.0
            val b = (p - m) / 2.0
            val expected = 4.0 * a + 2.0 * b + c
            if (abs(atTwo - expected) <= 1e-7 * max(1.0, abs(atTwo))) {
                if (abs(a) < 1e-10) return if (abs(b) < 1e-10) emptyList() else listOf(-c / b)
                val discriminant = b * b - 4.0 * a * c
                if (discriminant < -1e-10) return emptyList()
                if (abs(discriminant) <= 1e-10) return listOf(-b / (2.0 * a))
                val root = sqrt(discriminant)
                return listOf((-b - root) / (2.0 * a), (-b + root) / (2.0 * a)).sorted()
            }
        }
        val roots=mutableListOf<Double>(); var px=-1000.0; var py=runCatching { f(px) }.getOrNull()
        for(i in 1..8000) {
            val x=-1000.0+2000.0*i/8000.0; val y=runCatching { f(x) }.getOrNull()
            if(py!=null && y!=null) {
                if(abs(py)<1e-8) roots.add(px)
                if(py*y<0) {
                    var lo=px; var hi=x; var flo=py
                    repeat(64) { val mid=(lo+hi)/2; val fm=runCatching { f(mid) }.getOrNull() ?: return@repeat
                        if(abs(fm)<1e-12) { lo=mid; hi=mid } else if(flo*fm<=0) hi=mid else { lo=mid; flo=fm }
                    }
                    roots.add((lo+hi)/2)
                }
            }
            px=x; py=y
        }
        // Newton refinement from a coarse set of seeds also detects even-multiplicity
        // roots, which do not change sign and therefore cannot be found by bisection alone.
        for (seedIndex in 0..160) {
            var x = -1000.0 + 2000.0 * seedIndex / 160.0
            repeat(80) {
                val y = runCatching { f(x) }.getOrNull() ?: return@repeat
                if (abs(y) <= 1e-8 * max(1.0, abs(x))) {
                    roots.add(x)
                    return@repeat
                }
                val h = 1e-5 * max(1.0, abs(x))
                val left = runCatching { f(x - h) }.getOrNull()
                val right = runCatching { f(x + h) }.getOrNull()
                if (left == null || right == null) return@repeat
                val slope = (right - left) / (2.0 * h)
                if (!slope.isFinite() || abs(slope) < 1e-12) return@repeat
                val step = (y / slope).coerceIn(-100.0, 100.0)
                val next = x - step
                if (!next.isFinite() || next !in -1000.0..1000.0) return@repeat
                if (abs(next - x) < 1e-11) {
                    if (abs(y) < 1e-6) roots.add(next)
                    return@repeat
                }
                x = next
            }
        }
        return roots.filter { it.isFinite() }.sorted().fold(mutableListOf()) { acc, v ->
            if (acc.isEmpty() || abs(acc.last() - v) > 1e-4) acc.add(v)
            acc
        }
    }

    fun derivative(expression:String, at:Double):Double {
        val h=1e-5*max(1.0,abs(at)); return (evaluate(expression,at+h)-evaluate(expression,at-h))/(2*h)
    }
    fun integrate(expression:String, from:Double, to:Double):Double {
        require(from.isFinite() && to.isFinite())
        if(from==to) return 0.0
        val n=1000; val h=(to-from)/n
        var sum=evaluate(expression,from)+evaluate(expression,to)
        for(i in 1 until n) sum+=(if(i%2==0) 2.0 else 4.0)*evaluate(expression,from+i*h)
        return sum*h/3.0
    }
    fun graph(expression:String, from:Double, to:Double, count:Int=320):List<Point> {
        require(from<to) { "Minimum x must be less than maximum x." }
        return (0..count).mapNotNull { i ->
            val x=from+(to-from)*i/count; val y=runCatching { evaluate(expression,x) }.getOrNull()
            if(y==null || !y.isFinite() || abs(y)>1e9) null else Point(x,y)
        }
    }

    private class Parser(private val s:String, private val x:Double) {
        private var i=0
        private fun skip(){ while(i<s.length && s[i].isWhitespace()) i++ }
        private fun peek():Char? { skip(); return s.getOrNull(i) }
        private fun eat(c:Char):Boolean { skip(); if(s.getOrNull(i)==c){i++;return true};return false }
        fun parse():Double { require(s.isNotBlank()) { "Enter an expression." }; val v=expr();skip();require(i==s.length) { "Unexpected input near '${s.substring(i).take(12)}'." };return v }
        private fun expr():Double { var v=term(); while(true) v=when { eat('+')->v+term();eat('-')->v-term();else->return v } }
        private fun term():Double { var v=unary();while(true)v=when{eat('*')->v*unary();eat('/')->v/unary();eat('%')->v%unary();peek()?.let{it=='('||it.isLetter()||it=='π'}==true->v*unary();else->return v} }
        private fun unary():Double=when{eat('+')->unary();eat('-')->-unary();else->power()}
        private fun power():Double { val a=primary();return if(eat('^')) a.pow(unary()) else a }
        private fun primary():Double {
            skip()
            if(eat('(')){val v=expr();require(eat(')')){"Missing ')'."};return v}
            val c=s.getOrNull(i)?:error("Unexpected end.")
            if(c.isDigit()||c=='.') return number()
            if(c.isLetter()||c=='π') return identifier()
            error("Unexpected character '$c'.")
        }
        private fun number():Double {
            val start=i;while(i<s.length&&(s[i].isDigit()||s[i]=='.'))i++
            if(i<s.length&&s[i] in "eE"){i++;if(i<s.length&&s[i] in "+-")i++;while(i<s.length&&s[i].isDigit())i++}
            return s.substring(start,i).toDoubleOrNull()?:error("Invalid number.")
        }
        private fun identifier():Double {
            val start=i
            if(s[i]=='π')i++ else while(i<s.length&&(s[i].isLetterOrDigit()||s[i]=='_'))i++
            val name=s.substring(start,i).lowercase()
            if(name=="x")return x
            if(name=="pi"||name=="π")return PI
            if(name=="e")return E
            require(eat('(')) { "Unknown symbol '$name'." }
            val args=mutableListOf<Double>()
            if(!eat(')')){do{args.add(expr())}while(eat(','));require(eat(')')){"Missing ')' after $name."}}
            fun one(f:(Double)->Double):Double{require(args.size==1){"$name needs one argument."};return f(args[0])}
            return when(name){
                "sin"->one(::sin);"cos"->one(::cos);"tan"->one(::tan);"asin"->one(::asin);"acos"->one(::acos);"atan"->one(::atan)
                "sinh"->one(::sinh);"cosh"->one(::cosh);"tanh"->one(::tanh);"sqrt"->one(::sqrt);"cbrt"->one(::cbrt)
                "abs"->one(::abs);"exp"->one(::exp);"ln"->one(::ln);"log"->one(::log10);"floor"->one(::floor);"ceil"->one(::ceil)
                "min"->{require(args.size==2);min(args[0],args[1])};"max"->{require(args.size==2);max(args[0],args[1])}
                "pow"->{require(args.size==2);args[0].pow(args[1])};else->error("Unsupported function '$name'.")
            }
        }
    }
}
