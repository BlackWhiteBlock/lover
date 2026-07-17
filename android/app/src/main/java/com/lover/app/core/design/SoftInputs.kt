package com.lover.app.core.design

import android.widget.NumberPicker
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.min

private val SoftFieldShape = RoundedCornerShape(22.dp)
private val displayFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")

@Composable
fun softTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalMood.current.soft.copy(alpha = 0.55f),
    unfocusedBorderColor = LocalMood.current.softOutline,
    disabledBorderColor = LocalMood.current.softOutline.copy(alpha = 0.6f),
    focusedContainerColor = Color.White.copy(alpha = 0.96f),
    unfocusedContainerColor = LocalMood.current.softSurface,
    disabledContainerColor = LocalMood.current.softSurface.copy(alpha = 0.85f),
    cursorColor = LocalMood.current.soft,
    focusedLabelColor = LocalMood.current.accent,
    unfocusedLabelColor = LocalMood.current.stone,
    focusedTrailingIconColor = LocalMood.current.soft,
    unfocusedTrailingIconColor = LocalMood.current.stone,
)

@Composable
fun SoftTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val mood = LocalMood.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it, color = mood.stone.copy(alpha = 0.7f)) } },
        singleLine = singleLine && minLines == 1,
        minLines = minLines,
        readOnly = readOnly,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        trailingIcon = trailingIcon,
        leadingIcon = leadingIcon,
        shape = SoftFieldShape,
        colors = softTextFieldColors(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoverDateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    supportingText: String? = null,
) {
    val mood = LocalMood.current
    var showSheet by remember { mutableStateOf(false) }
    val parsed = remember(value) { runCatching { LocalDate.parse(value) }.getOrNull() }
    val display = parsed?.format(displayFormatter) ?: value.ifBlank { "请选择日期" }

    Column(modifier = modifier) {
        Box {
            SoftTextField(
                value = display,
                onValueChange = {},
                label = label,
                readOnly = true,
                trailingIcon = {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = mood.soft)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(SoftFieldShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showSheet = true },
                    ),
            )
        }
        if (supportingText != null) {
            Text(
                supportingText,
                style = MaterialTheme.typography.labelSmall,
                color = mood.stone,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            )
        }
    }

    if (showSheet) {
        LoverDatePickerSheet(
            initialDate = parsed ?: LocalDate.now(),
            minDate = minDate,
            maxDate = maxDate,
            title = label,
            onDismiss = { showSheet = false },
            onConfirm = { picked ->
                onValueChange(picked.toString())
                showSheet = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoverDatePickerSheet(
    initialDate: LocalDate,
    minDate: LocalDate?,
    maxDate: LocalDate?,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val mood = LocalMood.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lower = minDate ?: LocalDate.of(1970, 1, 1)
    val upper = maxDate ?: LocalDate.of(2100, 12, 31)
    val start = initialDate.coerceIn(lower, upper)

    var year by remember { mutableIntStateOf(start.year) }
    var month by remember { mutableIntStateOf(start.monthValue) }
    var day by remember { mutableIntStateOf(start.dayOfMonth) }

    val yearRange = lower.year..upper.year
    val monthRange = remember(year, lower, upper) {
        val minM = if (year == lower.year) lower.monthValue else 1
        val maxM = if (year == upper.year) upper.monthValue else 12
        minM..maxM
    }
    val dayRange = remember(year, month, lower, upper) {
        val dim = YearMonth.of(year, month).lengthOfMonth()
        val minD = if (year == lower.year && month == lower.monthValue) lower.dayOfMonth else 1
        val maxD = if (year == upper.year && month == upper.monthValue) {
            min(upper.dayOfMonth, dim)
        } else {
            dim
        }
        minD..maxD
    }

    LaunchedEffect(monthRange) {
        if (month !in monthRange) month = month.coerceIn(monthRange)
    }
    LaunchedEffect(dayRange) {
        if (day !in dayRange) day = day.coerceIn(dayRange)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mood.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .height(4.dp)
                    .fillMaxWidth(0.12f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(mood.softOutline),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = mood.accent,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                "上下滑动选择年月日",
                style = MaterialTheme.typography.labelMedium,
                color = mood.stone,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WheelNumberPicker(
                    value = year,
                    range = yearRange,
                    onValueChange = { year = it },
                    formatter = { "$it 年" },
                    modifier = Modifier.weight(1.2f),
                )
                WheelNumberPicker(
                    value = month,
                    range = monthRange,
                    onValueChange = { month = it },
                    formatter = { "$it 月" },
                    modifier = Modifier.weight(1f),
                )
                WheelNumberPicker(
                    value = day,
                    range = dayRange,
                    onValueChange = { day = it },
                    formatter = { "$it 日" },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = mood.stone)
                }
                Button(
                    onClick = {
                        val picked = LocalDate.of(year, month, day).coerceIn(lower, upper)
                        onConfirm(picked)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = mood.soft),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
                ) {
                    Text("确认")
                }
            }
        }
    }
}

@Composable
private fun WheelNumberPicker(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    formatter: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    val safeRange = if (range.first <= range.last) range else range.last..range.first
    val safeValue = value.coerceIn(safeRange)
    AndroidView(
        factory = { context ->
            NumberPicker(context).apply {
                wrapSelectorWheel = true
                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            }
        },
        update = { picker ->
            val minV = safeRange.first
            val maxV = safeRange.last
            val labels = Array(maxV - minV + 1) { i -> formatter(minV + i) }
            picker.setOnValueChangedListener(null)
            picker.displayedValues = null
            if (picker.maxValue < minV) {
                picker.minValue = minV
                picker.maxValue = maxV
            } else {
                picker.maxValue = maxOf(picker.maxValue, maxV)
                picker.minValue = minV
                picker.maxValue = maxV
            }
            picker.displayedValues = labels
            picker.value = safeValue
            picker.setOnValueChangedListener { _, _, newVal -> onValueChange(newVal) }
        },
        modifier = modifier.height(160.dp),
    )
}
