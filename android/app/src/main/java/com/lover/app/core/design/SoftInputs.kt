package com.lover.app.core.design

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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

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
                Modifier
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
    val selectable = remember(minDate, maxDate) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = utcTimeMillis.toUtcLocalDate()
                if (minDate != null && date.isBefore(minDate)) return false
                if (maxDate != null && date.isAfter(maxDate)) return false
                return true
            }
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toUtcEpochMillis(),
        selectableDates = selectable,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mood.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .height(4.dp)
                    .fillMaxWidth(0.12f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(mood.softOutline),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = mood.accent,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            DatePicker(
                state = state,
                title = null,
                headline = null,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = mood.background,
                    selectedDayContainerColor = mood.soft,
                    selectedDayContentColor = Color.White,
                    todayDateBorderColor = mood.soft,
                    selectedYearContainerColor = mood.soft,
                    dayContentColor = Color(0xFF332927),
                ),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = mood.stone)
                }
                Button(
                    onClick = {
                        val millis = state.selectedDateMillis ?: return@Button
                        onConfirm(millis.toUtcLocalDate())
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

private fun LocalDate.toUtcEpochMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
