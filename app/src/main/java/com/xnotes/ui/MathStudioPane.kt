package com.xnotes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.math.MathStudioEngine
import com.xnotes.math.MathStudioAiClient
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private enum class StudioMode(val title:String){CALCULATE("Calculate"),SOLVE("Solve"),GEOMETRY("Geometry"),DERIVATIVE("Derivative"),INTEGRAL("Integral"),GRAPH("Graph")}

/** Created only while the Math Studio destination is open. */
@Composable
fun MathStudioPane(editor:Editor){
    var mode by rememberSaveable{mutableStateOf(StudioMode.CALCULATE.name)}
    var expression by rememberSaveable{mutableStateOf("x^2 - 4")}
    var at by rememberSaveable{mutableStateOf("2")}
    var lower by rememberSaveable{mutableStateOf("-5")}
    var upper by rememberSaveable{mutableStateOf("5")}
    var output by rememberSaveable{mutableStateOf("Choose a tool and enter a problem.")}
    var insertText by rememberSaveable{mutableStateOf("")}
    var graph by remember{mutableStateOf(emptyList<MathStudioEngine.Point>())}
    var error by remember{mutableStateOf<String?>(null)}
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("saya_math_studio", 0) }
    var endpoint by remember { mutableStateOf(prefs.getString("endpoint", "") ?: "") }
    var accessToken by remember { mutableStateOf(prefs.getString("access_token", "") ?: "") }
    var aiAnswer by rememberSaveable { mutableStateOf("") }
    var aiBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val selected=StudioMode.valueOf(mode)
    fun calculate(){
        error=null;graph=emptyList()
        try{
            output=when(selected){
                StudioMode.CALCULATE->{val v=MathStudioEngine.evaluate(expression);"Result\n$expression = ${fmt(v)}\n\nStandard operator precedence is applied."}
                StudioMode.SOLVE->{val roots=MathStudioEngine.solveEquation(expression);if(roots.isEmpty())"No real roots were found in the searched range." else "Solutions\n"+roots.joinToString("\n"){"x = ${fmt(it)}"}+"\n\nLinear and quadratic equations are solved analytically; other equations use numerical bisection."}
                StudioMode.GEOMETRY->{
                    val shape=expression.trim().lowercase()
                    val a=lower.toDoubleOrNull() ?: throw IllegalArgumentException("Enter a valid first dimension.")
                    val b=upper.toDoubleOrNull() ?: 0.0
                    require(a.isFinite() && a>0) { "Dimensions must be positive numbers." }
                    fun second()=b.also { require(it.isFinite() && it>0) { "Enter a positive second dimension." } }
                    when(shape){
                        "circle"->"Circle\nRadius = ${fmt(a)}\nArea = ${fmt(Math.PI*a*a)}\nCircumference = ${fmt(2*Math.PI*a)}"
                        "triangle"->{val h=second();"Triangle (base × perpendicular height)\nBase = ${fmt(a)}, height = ${fmt(h)}\nArea = ½ × base × height = ${fmt(a*h/2)}"}
                        "rectangle"->{val h=second();"Rectangle\nArea = base × height = ${fmt(a*h)}\nPerimeter = 2(base + height) = ${fmt(2*(a+h))}"}
                        "square"->"Square\nSide = ${fmt(a)}\nArea = side² = ${fmt(a*a)}\nPerimeter = 4 × side = ${fmt(4*a)}\nDiagonal = side × √2 = ${fmt(a*kotlin.math.sqrt(2.0))}"
                        "pythagoras"->{val h=second();"Right triangle\na = ${fmt(a)}, b = ${fmt(h)}\nc = √(a² + b²) = ${fmt(kotlin.math.hypot(a,h))}"}
                        "sphere"->"Sphere\nRadius = ${fmt(a)}\nVolume = ⁴⁄₃πr³ = ${fmt(4.0/3.0*Math.PI*a*a*a)}\nSurface area = 4πr² = ${fmt(4*Math.PI*a*a)}"
                        "cylinder"->{val h=second();"Cylinder\nRadius = ${fmt(a)}, height = ${fmt(h)}\nVolume = πr²h = ${fmt(Math.PI*a*a*h)}\nCurved surface area = 2πrh = ${fmt(2*Math.PI*a*h)}\nTotal surface area = 2πr(r+h) = ${fmt(2*Math.PI*a*(a+h))}"}
                        "cone"->{val h=second();val slant=kotlin.math.hypot(a,h);"Right cone\nRadius = ${fmt(a)}, height = ${fmt(h)}\nSlant height = √(r²+h²) = ${fmt(slant)}\nVolume = ⅓πr²h = ${fmt(Math.PI*a*a*h/3)}\nCurved surface area = πrl = ${fmt(Math.PI*a*slant)}"}
                        "cube"->"Cube\nSide = ${fmt(a)}\nVolume = side³ = ${fmt(a*a*a)}\nTotal surface area = 6side² = ${fmt(6*a*a)}\nSpace diagonal = side√3 = ${fmt(a*kotlin.math.sqrt(3.0))}"
                        else->throw IllegalArgumentException("Choose circle, triangle, rectangle, square, pythagoras, sphere, cylinder, cone or cube.")
                    }
                }
                StudioMode.DERIVATIVE->{val x=at.toDouble();val d=MathStudioEngine.derivative(expression,x);"Numerical derivative\nf'(${fmt(x)}) ≈ ${fmt(d)}\n\nCentred finite difference: [f(x+h) − f(x−h)] / (2h)."}
                StudioMode.INTEGRAL->{val a=lower.toDouble();val b=upper.toDouble();val v=MathStudioEngine.integrate(expression,a,b);"Definite integral\n∫[${fmt(a)}, ${fmt(b)}] $expression dx ≈ ${fmt(v)}\n\nComposite Simpson's rule (1,000 subintervals)."}
                StudioMode.GRAPH->{val a=lower.toDouble();val b=upper.toDouble();graph=MathStudioEngine.graph(expression,a,b);"Graph of y = $expression\nx from ${fmt(a)} to ${fmt(b)} · ${graph.size} samples"}
            }
            insertText="Math Studio\n\n$output"
        }catch(t:Throwable){error=t.message?:"Unable to calculate.";output="Check the expression and try again.";insertText=""}
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Text("Math Studio",style=MaterialTheme.typography.headlineMedium)
        Text("Calculate, solve equations, explore calculus and plot functions.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
            StudioMode.entries.forEach{m->FilterChip(selected==m,onClick={mode=m.name;error=null},label={Text(m.title,fontSize=11.sp)})}
        }
        OutlinedTextField(expression,{expression=it},Modifier.fillMaxWidth(),label={Text(when(selected){StudioMode.SOLVE->"Equation, e.g. x^2 - 4 = 0";StudioMode.GEOMETRY->"Shape: circle, triangle, rectangle, square, pythagoras, sphere, cylinder, cone, cube";else->"Expression in x"})},supportingText={Text("Use ^, pi or π, and sin(x), cos(x), sqrt(x), ln(x), log(x), abs(x).")},maxLines=3)
        if(selected==StudioMode.DERIVATIVE)OutlinedTextField(at,{at=it},Modifier.fillMaxWidth(),label={Text("Evaluate at x")})
        if(selected==StudioMode.INTEGRAL||selected==StudioMode.GRAPH||selected==StudioMode.GEOMETRY)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(lower,{lower=it},Modifier.weight(1f),label={Text(if(selected==StudioMode.GEOMETRY)"Radius / side a" else "From / min x")})
            OutlinedTextField(upper,{upper=it},Modifier.weight(1f),label={Text(if(selected==StudioMode.GEOMETRY)"Side b / height" else "To / max x")})
        }
        Button(::calculate,Modifier.fillMaxWidth()){Text("Calculate")}
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Solution",style=MaterialTheme.typography.titleMedium);Text(output)}}
        if(selected==StudioMode.GRAPH&&graph.isNotEmpty())GraphPlot(graph,Modifier.fillMaxWidth().height(230.dp))
        if(insertText.isNotBlank())OutlinedButton({editor.insertMathStudioResult(insertText)},Modifier.fillMaxWidth()){Text("Insert solution into current notebook")}
        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                Text("AI tutor",style=MaterialTheme.typography.titleSmall)
                Text("Connect a deployed HTTPS proxy. Its provider key stays on the server and is never stored in this app.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value=endpoint,
                    onValueChange={endpoint=it;prefs.edit().putString("endpoint",it).apply()},
                    modifier=Modifier.fillMaxWidth(),
                    label={Text("Secure AI backend URL (HTTPS)")},
                    singleLine=true,
                )
                OutlinedTextField(
                    value=accessToken,
                    onValueChange={accessToken=it;prefs.edit().putString("access_token",it).apply()},
                    modifier=Modifier.fillMaxWidth(),
                    label={Text("Backend access token")},
                    singleLine=true,
                )
                Button(
                    onClick={
                        scope.launch {
                            aiBusy=true; aiAnswer=""
                            try {
                                aiAnswer=withContext(Dispatchers.IO){MathStudioAiClient.explain(endpoint,expression,output,accessToken)}
                                insertText="Math Studio\n\n$output\n\nAI tutor explanation\n\n$aiAnswer"
                            } catch(t:Throwable){aiAnswer=t.message?:"AI explanation failed."}
                            finally { aiBusy=false }
                        }
                    },
                    enabled=endpoint.startsWith("https://") && !aiBusy && output.isNotBlank(),
                    modifier=Modifier.fillMaxWidth(),
                ){Text(if(aiBusy)"Thinking…" else "Explain with AI")}
                if(aiAnswer.isNotBlank())Text(aiAnswer,style=MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun fmt(v:Double)=if(!v.isFinite())"undefined" else if(abs(v)<1e-10)"0" else "%.8g".format(v)

@Composable
private fun GraphPlot(points:List<MathStudioEngine.Point>,modifier:Modifier=Modifier){
    val axis=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.55f);val curve=MaterialTheme.colorScheme.primary
    Canvas(modifier.background(MaterialTheme.colorScheme.surfaceVariant)){
        if(points.size<2)return@Canvas
        val minX=points.minOf{it.x};val maxX=points.maxOf{it.x};val minY=points.minOf{it.y};val maxY=points.maxOf{it.y}
        val spanY=(maxY-minY).takeIf{it>1e-9}?:1.0;val pad=18.dp.toPx();val w=(size.width-2*pad).coerceAtLeast(1f);val h=(size.height-2*pad).coerceAtLeast(1f)
        val zx=if(minX<=0&&maxX>=0)pad+(-minX).toFloat()/(maxX-minX).toFloat()*w else pad
        val zy=if(minY<=0&&maxY>=0)pad+(maxY/spanY).toFloat()*h else pad+h
        drawLine(axis,Offset(zx,pad),Offset(zx,pad+h),1.dp.toPx());drawLine(axis,Offset(pad,zy),Offset(pad+w,zy),1.dp.toPx())
        val path=Path();var started=false;var lastY=0f
        points.forEach{p->val px=pad+((p.x-minX)/(maxX-minX)).toFloat()*w;val py=pad+((maxY-p.y)/spanY).toFloat()*h
            if(!started||abs(py-lastY)>h*1.5f){path.moveTo(px,py);started=true}else path.lineTo(px,py);lastY=py}
        drawPath(path,curve,style=Stroke(2.5.dp.toPx(),cap=StrokeCap.Round))
    }
}
