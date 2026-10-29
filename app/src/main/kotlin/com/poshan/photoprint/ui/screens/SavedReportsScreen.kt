package com.poshan.photoprint.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.poshan.photoprint.core.export.ExportHelper
import com.poshan.photoprint.core.model.ReportRecord
import com.poshan.photoprint.ui.PoshanStrings
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedReportsScreen(
    reports: List<ReportRecord>,
    isHindi: Boolean = false,
    onBack: () -> Unit,
    onOpenReport: (ReportRecord) -> Unit,
    onDeleteReport: (String) -> Unit
) {
    val context = LocalContext.current
    var reportToDelete by remember { mutableStateOf<ReportRecord?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = PoshanStrings.savedReportsTitle(isHindi),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = if (isHindi) "कुल ${reports.size} रिपोर्ट्स सुरक्षित हैं" else "${reports.size} reports saved",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        if (reports.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Text(
                        text = PoshanStrings.noReportsYet(isHindi),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = if (isHindi)
                            "नई रिपोर्ट बनाकर A4 प्रिंट या डाउनलोड करने पर वह यहाँ अपने आप सुरक्षित हो जाएगी।"
                        else
                            "When you create and export an A4 report, it will be automatically archived here.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(onClick = onBack) {
                        Text(if (isHindi) "नई रिपोर्ट बनाएं" else "Create New Report")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isHindi)
                            "यहाँ से आप पुरानी रिपोर्ट सीधे WhatsApp पर भेज सकते हैं या प्रिंट कर सकते हैं:"
                        else
                            "Re-share or print any previous monthly reports directly:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(reports, key = { it.id }) { report ->
                    SavedReportCard(
                        report = report,
                        isHindi = isHindi,
                        onOpenReport = { onOpenReport(report) },
                        onShareWhatsApp = {
                            ExportHelper.shareToWhatsApp(context, File(report.pdfPath))
                        },
                        onPrint = {
                            ExportHelper.printDocument(context, File(report.pdfPath), report.displayTitle)
                        },
                        onDelete = { reportToDelete = report }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // Confirmation Dialog before deleting saved report
    reportToDelete?.let { report ->
        AlertDialog(
            onDismissRequest = { reportToDelete = null },
            title = {
                Text(
                    text = if (isHindi) "रिपोर्ट हटाएं?" else "Delete Report?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isHindi)
                        "क्या आप वाकई '${report.displayTitle}' को हटाना चाहते हैं? यह फाइल हमेशा के लिए हटा दी जाएगी।"
                    else
                        "Are you sure you want to delete '${report.displayTitle}'? This action cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteReport(report.id)
                        reportToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(if (isHindi) "हाँ, हटाएं" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { reportToDelete = null }) {
                    Text(if (isHindi) "रद्द करें" else "Cancel")
                }
            }
        )
    }
}

@Composable
private fun SavedReportCard(
    report: ReportRecord,
    isHindi: Boolean,
    onOpenReport: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onPrint: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenReport() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail or Document Icon (Tappable to Edit)
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (report.imagePath != null && File(report.imagePath).exists()) {
                    AsyncImage(
                        model = File(report.imagePath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info & Metadata
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = report.displayTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${report.photoCount} Photos • ${report.formattedDate}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Actions Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Open / Edit Report in Main Mode
                    FilledTonalIconButton(
                        onClick = onOpenReport,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Report", modifier = Modifier.size(18.dp))
                    }

                    // WhatsApp Share
                    FilledTonalIconButton(
                        onClick = onShareWhatsApp,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0xFF25D366).copy(alpha = 0.2f),
                            contentColor = Color(0xFF1B8A44)
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "WhatsApp", modifier = Modifier.size(18.dp))
                    }

                    // Print
                    FilledTonalIconButton(
                        onClick = onPrint,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Print, contentDescription = "Print", modifier = Modifier.size(18.dp))
                    }

                    // Delete
                    FilledTonalIconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
