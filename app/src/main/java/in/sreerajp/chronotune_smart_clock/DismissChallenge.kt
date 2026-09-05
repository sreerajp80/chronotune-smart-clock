package `in`.sreerajp.chronotune_smart_clock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

// Challenge type identifiers, matching the values stored on Alarm.dismissChallenge.
object DismissChallengeType {
    const val NONE = "NONE"
    const val MATH = "MATH"
    const val PHRASE = "PHRASE"
    const val MEMORY = "MEMORY"
}

/**
 * Full-screen wake-up challenge shown over the ringing overlay. Runs [count] rounds of the
 * chosen [challengeType]; every correct answer advances one round. On the last correct answer
 * it calls [onSolved]; [onCancel] returns to the ringing screen (the alarm keeps ringing).
 *
 * [onSolved] reports how many answers were given in total (wrong ones included) and how long
 * the challenge took. Those two numbers are what let the history tell a challenge solved
 * first-try in four seconds — a half-asleep dismiss — from one that took six attempts.
 */
@Composable
fun DismissChallengePanel(
    challengeType: String,
    difficulty: String,
    count: Int,
    onSolved: (attempts: Int, elapsedMs: Long) -> Unit,
    onCancel: () -> Unit
) {
    val total = count.coerceIn(1, 10)
    var solved by remember { mutableIntStateOf(0) }
    // Wrong answers so far, and when the panel opened. Kept across rounds, so the numbers
    // describe the whole challenge rather than the last round of it.
    var wrongAnswers by remember { mutableIntStateOf(0) }
    val openedAt = remember { System.currentTimeMillis() }

    fun finish() {
        // Every correct round plus every wrong answer = all the answers the user gave.
        onSolved(total + wrongAnswers, System.currentTimeMillis() - openedAt)
    }

    // Advance one round; finish when all rounds are done.
    fun roundPassed() {
        val next = solved + 1
        if (next >= total) finish() else solved = next
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Consume all taps landing outside inputs
                detectTapGestures { }
            }
            .background(Color.Black.copy(alpha = 0.97f))
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar: back to ring + progress
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to alarm",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }
            if (total > 1) {
                Text(
                    text = "${(solved + 1).coerceAtMost(total)} / $total",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.size(40.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "SOLVE TO DISMISS",
            color = Color(0xFFD32F2F),
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 3.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        // The active challenge. `key(solved)` forces a fresh problem for each round.
        key(challengeType, difficulty, solved) {
            when (challengeType) {
                DismissChallengeType.MATH -> MathChallenge(
                    difficulty,
                    onCorrect = { roundPassed() },
                    onWrong = { wrongAnswers++ }
                )
                DismissChallengeType.PHRASE -> PhraseChallenge(
                    difficulty,
                    onCorrect = { roundPassed() },
                    onWrong = { wrongAnswers++ }
                )
                DismissChallengeType.MEMORY -> MemoryChallenge(
                    difficulty,
                    onCorrect = { roundPassed() },
                    onWrong = { wrongAnswers++ }
                )
                else -> finish() // Unknown type: don't trap the user.
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

// ============================================================================
// MATH — solve an arithmetic problem using an on-screen keypad.
// ============================================================================

private data class MathProblem(val text: String, val answer: Int)

private fun generateMathProblem(difficulty: String): MathProblem {
    return when (difficulty) {
        "HARD" -> {
            // Multiplication / integer division with larger operands.
            if (Random.nextBoolean()) {
                val a = Random.nextInt(6, 13)
                val b = Random.nextInt(6, 13)
                MathProblem("$a × $b", a * b)
            } else {
                val b = Random.nextInt(3, 13)
                val ans = Random.nextInt(3, 13)
                val a = b * ans
                MathProblem("$a ÷ $b", ans)
            }
        }
        "MEDIUM" -> {
            when (Random.nextInt(3)) {
                0 -> {
                    val a = Random.nextInt(5, 21); val b = Random.nextInt(5, 21)
                    MathProblem("$a + $b", a + b)
                }
                1 -> {
                    val a = Random.nextInt(10, 31); val b = Random.nextInt(1, a)
                    MathProblem("$a − $b", a - b)
                }
                else -> {
                    val a = Random.nextInt(2, 10); val b = Random.nextInt(2, 10)
                    MathProblem("$a × $b", a * b)
                }
            }
        }
        else -> {
            // EASY: addition / subtraction within 10, non-negative result.
            if (Random.nextBoolean()) {
                val a = Random.nextInt(1, 10); val b = Random.nextInt(1, 10)
                MathProblem("$a + $b", a + b)
            } else {
                val a = Random.nextInt(2, 11); val b = Random.nextInt(1, a)
                MathProblem("$a − $b", a - b)
            }
        }
    }
}

@Composable
private fun MathChallenge(
    difficulty: String,
    onCorrect: () -> Unit,
    onWrong: () -> Unit = {}
) {
    val problem = remember { generateMathProblem(difficulty) }
    var entry by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${problem.text} = ?",
            color = Color.White,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(
                    2.dp,
                    if (wrong) Color(0xFFD32F2F) else Color.White.copy(alpha = 0.4f),
                    RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = entry.ifEmpty { "—" },
                color = if (entry.isEmpty()) Color.White.copy(alpha = 0.3f) else Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        if (wrong) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Try again", color = Color(0xFFEF9A9A), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(24.dp))
        NumberKeypad(
            onDigit = { d ->
                wrong = false
                if (entry.length < 6) entry += d
            },
            onBackspace = {
                wrong = false
                entry = entry.dropLast(1)
            },
            onSubmit = {
                val value = entry.toIntOrNull()
                if (value != null && value == problem.answer) onCorrect()
                else { wrong = true; onWrong(); entry = "" }
            }
        )
    }
}

@Composable
private fun NumberKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9")
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { d -> KeypadKey(label = d) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KeypadKey(icon = Icons.AutoMirrored.Filled.Backspace, onClick = onBackspace)
            KeypadKey(label = "0") { onDigit("0") }
            KeypadKey(label = "OK", accent = true, onClick = onSubmit)
        }
    }
}

@Composable
private fun KeypadKey(
    label: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (accent) Color(0xFFD32F2F) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(18.dp)
            )
            .border(
                1.dp,
                if (accent) Color.Transparent else Color.White.copy(alpha = 0.2f),
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            icon != null -> Icon(icon, contentDescription = "Backspace", tint = Color.White, modifier = Modifier.size(24.dp))
            else -> Text(
                text = label ?: "",
                color = Color.White,
                fontSize = if (label == "OK") 18.sp else 26.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================================
// PHRASE — retype a random word (Easy) or sentence (Medium/Hard) exactly.
// ============================================================================

private val EASY_WORDS = listOf(
    "sunrise", "morning", "awake", "coffee", "window", "breakfast", "daylight", "refresh"
)
private val PHRASES = listOf(
    "The early bird catches the worm",
    "Time to start a brand new day",
    "I am awake and ready to go",
    "Rise and shine it is morning",
    "A good day begins right now",
    "Get up and seize the day"
)

@Composable
private fun PhraseChallenge(
    difficulty: String,
    onCorrect: () -> Unit,
    onWrong: () -> Unit = {}
) {
    val target = remember {
        if (difficulty == "EASY") EASY_WORDS.random() else PHRASES.random()
    }
    var entry by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    fun normalize(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
    ) {
        Text("Type this exactly", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = target,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        OutlinedTextField(
            value = entry,
            onValueChange = { entry = it; wrong = false },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            isError = wrong,
            placeholder = { Text("Type here", color = Color.White.copy(alpha = 0.4f)) },
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White.copy(alpha = 0.6f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                cursorColor = Color.White
            )
        )
        if (wrong) {
            Spacer(modifier = Modifier.height(6.dp))
            Text("Does not match yet", color = Color(0xFFEF9A9A), fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = {
                if (normalize(entry) == normalize(target)) onCorrect()
                else { wrong = true; onWrong() }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Confirm", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// ============================================================================
// MEMORY — tap the shuffled number tiles in ascending order.
// ============================================================================

@Composable
private fun MemoryChallenge(
    difficulty: String,
    onCorrect: () -> Unit,
    onWrong: () -> Unit = {}
) {
    val n = when (difficulty) {
        "HARD" -> 9
        "MEDIUM" -> 6
        else -> 4
    }
    val tiles = remember { (1..n).shuffled() }
    var next by remember { mutableIntStateOf(1) }
    var wrong by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Tap the numbers in order", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Next: $next",
            color = if (wrong) Color(0xFFEF9A9A) else Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(20.dp))
        // Lay out tiles in rows of 3.
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                row.forEach { value ->
                    val done = value < next
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (done) Color(0xFF2E7D32).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(18.dp)
                            )
                            .border(
                                1.5.dp,
                                if (done) Color(0xFF66BB6A) else Color.White.copy(alpha = 0.3f),
                                RoundedCornerShape(18.dp)
                            )
                            .clickable(enabled = !done) {
                                if (value == next) {
                                    wrong = false
                                    if (value == n) onCorrect() else next = value + 1
                                } else {
                                    // Wrong tile — restart this round.
                                    wrong = true
                                    onWrong()
                                    next = 1
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = value.toString(),
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        if (wrong) {
            Text("Wrong order — start again", color = Color(0xFFEF9A9A), fontSize = 13.sp)
        }
    }
}
