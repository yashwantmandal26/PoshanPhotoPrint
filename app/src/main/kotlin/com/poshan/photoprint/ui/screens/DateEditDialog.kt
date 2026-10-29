package com.poshan.photoprint.ui.screens

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.poshan.photoprint.R
import com.poshan.photoprint.core.model.PhotoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateEditDialog(
    photo: PhotoItem,
    onDismiss: () -> Unit,
    onConfirm: (newDateMillis: Long) -> Unit
) {
    val initialDate = photo.dateMillis ?: System.currentTimeMillis()
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate,
        initialDisplayedMonthMillis = initialDate
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = datePickerState.selectedDateMillis ?: initialDate
                    onConfirm(selected)
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}
