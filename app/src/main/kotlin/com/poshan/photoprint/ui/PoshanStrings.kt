package com.poshan.photoprint.ui

object PoshanStrings {

    fun appTitle(isHindi: Boolean): String =
        if (isHindi) "पोषण फोटो प्रिंट" else "Poshan Photo Print"

    fun appSubtitle(isHindi: Boolean): String =
        if (isHindi) "आँगनबाड़ी केंद्र A4 फोटो रिपोर्ट" else "Anganwadi A4 Photo Report"

    fun selectPhotos(isHindi: Boolean): String =
        if (isHindi) "गैलरी से फोटो चुनें" else "Select Photos from Gallery"

    fun selectPhotosDesc(isHindi: Boolean): String =
        if (isHindi)
            "GPS Map Camera से ली गई फोटो चुनें। ऐप उन्हें तारीख के अनुसार 2-कॉलम ग्रिड में अपने आप व्यवस्थित कर देगा।"
        else
            "Choose photos taken with GPS Map Camera. The app will auto-sort them in ascending date order into a neat 2-column grid."

    fun savedReportsBtn(isHindi: Boolean, count: Int): String =
        if (isHindi) "सेव की गई रिपोर्ट ($count)" else "Saved Reports ($count)"

    fun keyFeaturesTitle(isHindi: Boolean): String =
        if (isHindi) "मुख्य सुविधाएं" else "Key Features"

    fun feature1Title(isHindi: Boolean): String =
        if (isHindi) "तारीख अनुसार ऑटो-सॉर्ट" else "Ascending Date Auto-Sort"

    fun feature1Desc(isHindi: Boolean): String =
        if (isHindi) "GPS स्टैम्प व OCR से तारीख पहचान कर सही बढ़ते क्रम में सजाता है।"
        else "Reads GPS Map Camera timestamps (EXIF & OCR) and sorts them chronologically."

    fun feature2Title(isHindi: Boolean): String =
        if (isHindi) "सख्त 2-कॉलम ग्रिड (Zero Crop)" else "Strict 2-Column Grid Always"

    fun feature2Desc(isHindi: Boolean): String =
        if (isHindi) "R1C1, R1C2, R2C1... बिना किसी फोटो को काटे, सभी वाटरमार्क और QR कोड दिखते हैं।"
        else "R1C1, R1C2, R2C1… with zero cropping. All watermarks and QR codes remain fully visible."

    fun feature3Title(isHindi: Boolean): String =
        if (isHindi) "छोटा PDF साइज और डायरेक्ट शेयर" else "Compressed PDF & Direct Sharing"

    fun feature3Desc(isHindi: Boolean): String =
        if (isHindi) "कम स्टोरेज (<600 KB) में व्हाट्सएप और प्रिंटर पर डायरेक्ट भेजने के लिए।"
        else "Optimized low storage (<600 KB) for instant WhatsApp sharing and direct printing."

    // A4 Preview
    fun a4Title(isHindi: Boolean): String =
        if (isHindi) "A4 फोटो रिपोर्ट" else "A4 Photo Report"

    fun a4Subtitle(isHindi: Boolean, count: Int): String =
        if (isHindi) "$count फोटो • 2-कॉलम ग्रिड" else "$count Photos • 2-Column Grid"

    fun downloadPdf(isHindi: Boolean): String =
        if (isHindi) "PDF डाउनलोड करें" else "Download PDF"

    fun saveImage(isHindi: Boolean): String =
        if (isHindi) "इमेज सेव करें" else "Save Image"

    fun whatsApp(isHindi: Boolean): String =
        if (isHindi) "व्हाट्सएप" else "WhatsApp"

    fun print(isHindi: Boolean): String =
        if (isHindi) "प्रिंट करें" else "Print"

    fun resetTooltip(isHindi: Boolean): String =
        if (isHindi) "तारीख क्रम और ज़ूम रीसेट करें" else "Reset Sort & Zoom"

    fun gestureTip(isHindi: Boolean): String =
        if (isHindi) "💡 फोटो को दबाकर रखें और ड्रैग करें • ज़ूम के लिए पिंच करें"
        else "💡 Touch & hold photo to drag and reorder • Pinch to zoom anywhere"

    fun swapHintBanner(isHindi: Boolean, index: Int): String =
        if (isHindi) "फोटो #${index + 1} चुनी गई। बदलने के लिए दूसरी फोटो पर टैप करें।"
        else "Photo #${index + 1} selected. Tap any other photo to swap position."

    fun cancel(isHindi: Boolean): String =
        if (isHindi) "रद्द करें" else "Cancel"

    fun dropHere(isHindi: Boolean): String =
        if (isHindi) "यहाँ बदलें" else "Drop Here"

    fun swapNotification(isHindi: Boolean, a: Int, b: Int): String =
        if (isHindi) "फोटो #$a और #$b का स्थान बदल दिया गया" else "Swapped #$a and #$b"

    fun resetNotification(isHindi: Boolean): String =
        if (isHindi) "मूल तारीख क्रम में रीसेट किया गया" else "Reset to ascending date order"

    fun pdfSavedToast(isHindi: Boolean): String =
        if (isHindi) "PDF 'Downloads' फोल्डर में सेव हो गई!" else "PDF downloaded to Downloads folder!"

    fun imageSavedToast(isHindi: Boolean): String =
        if (isHindi) "A4 इमेज फोन गैलरी (PoshanPhotoPrint) में सेव हो गई!" else "A4 Image saved to Phone Gallery!"

    fun preparingPdf(isHindi: Boolean): String =
        if (isHindi) "PDF तैयार हो रही है..." else "Preparing PDF..."

    fun preparingImage(isHindi: Boolean): String =
        if (isHindi) "इमेज तैयार हो रही है..." else "Preparing Image..."

    // Saved Reports Screen
    fun savedReportsTitle(isHindi: Boolean): String =
        if (isHindi) "सेव की गई रिपोर्ट" else "Saved Reports Archive"

    fun noReportsYet(isHindi: Boolean): String =
        if (isHindi) "अभी तक कोई रिपोर्ट सेव नहीं हुई है" else "No saved reports yet"

    fun deleteConfirm(isHindi: Boolean): String =
        if (isHindi) "रिपोर्ट हटा दी गई" else "Report deleted"
}
