package com.example.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.VideoFile
import com.example.R

object ToolRegistry {
  val tools: List<Tool> = listOf(
    // Tool 1: Image Compressor
    Tool(
      id = 1,
      category = Category.IMAGE,
      phase = 2,
      nameRes = R.string.tool1_name,
      descRes = R.string.tool1_desc,
      icon = Icons.Default.Compress,
      defaultName = "Image Compressor",
      keywords = listOf(
        "image compressor", "compress image", "shrink photo", "shrink image", "shrink", "reduce size", "photo", "jpg", "png", "webp",
        "تصویر کمپریسر", "تصویر سائز کم کریں", "تصویر",
        "इमेज कंप्रेसर", "फोटो कंप्रेस", "छवि का आकार"
      )
    ),

    // Tool 2: Image Resizer
    Tool(
      id = 2,
      category = Category.IMAGE,
      phase = 2,
      nameRes = R.string.tool2_name,
      descRes = R.string.tool2_desc,
      icon = Icons.Default.AspectRatio,
      defaultName = "Image Resizer",
      keywords = listOf(
        "image resizer", "resize image", "scale photo", "dimensions", "pixels", "width", "height",
        "تصویر کا سائز تبدیل کنندہ", "تصویر ری سائز",
        "इमेज रिसाइज़र", "फोटो साइज", "आयाम"
      )
    ),

    // Tool 3: Image Converter
    Tool(
      id = 3,
      category = Category.IMAGE,
      phase = 2,
      nameRes = R.string.tool3_name,
      descRes = R.string.tool3_desc,
      icon = Icons.Default.Transform,
      defaultName = "Image Converter",
      keywords = listOf(
        "image converter", "convert image", "jpg png webp", "png to jpg", "jpg to webp", "format",
        "تصویر کنورٹر", "تصویر فارمیٹ تبدیل",
        "इमेज कनवर्टर", "फोटो कनवर्टर", "प्रारूप"
      )
    ),

    // Tool 4: Image Cropper
    Tool(
      id = 4,
      category = Category.IMAGE,
      phase = 3,
      nameRes = R.string.tool4_name,
      descRes = R.string.tool4_desc,
      icon = Icons.Default.Crop,
      defaultName = "Image Cropper",
      keywords = listOf(
        "image cropper", "crop image", "crop", "cut photo", "aspect ratio", "square",
        "تصویر تراشیں", "تصویر کراپ", "کراپ",
        "इमेज क्रॉपर", "फोटो क्रॉप", "काटें", "क्रॉप"
      )
    ),

    // Tool 5: Remove Image Metadata
    Tool(
      id = 5,
      category = Category.IMAGE,
      phase = 2,
      nameRes = R.string.tool5_name,
      descRes = R.string.tool5_desc,
      icon = Icons.Default.CleaningServices,
      defaultName = "Remove Image Metadata",
      keywords = listOf(
        "remove image metadata", "strip exif", "exif remover", "exif", "gps", "metadata", "xmp", "c2pa", "privacy", "gps remove", "location",
        "تصویر کا میٹا ڈیٹا ہٹائیں", "میٹا ڈیٹا", "ایگزف", "جی پی ایس",
        "इमेज मेटाडेटा हटाएं", "मेटाडेटा", "एक्ज़िफ", "जीपीएस"
      )
    ),

    // Tool 6: File Information
    Tool(
      id = 6,
      category = Category.FILES,
      phase = 3,
      nameRes = R.string.tool6_name,
      descRes = R.string.tool6_desc,
      icon = Icons.Default.Info,
      defaultName = "File Information",
      keywords = listOf(
        "file information", "file info", "mime type", "details", "properties", "size",
        "فائل کی معلومات", "فائل تفصیلات", "تفصیلات",
        "फ़ाइल जानकारी", "फ़ाइल विवरण", "गुण", "विवरण"
      )
    ),

    // Tool 7: SHA-256 Hash Generator
    Tool(
      id = 7,
      category = Category.FILES,
      phase = 3,
      nameRes = R.string.tool7_name,
      descRes = R.string.tool7_desc,
      icon = Icons.Default.Fingerprint,
      defaultName = "SHA-256 Hash Generator",
      keywords = listOf(
        "sha-256 hash generator", "sha256", "hash", "checksum", "integrity", "verify",
        "SHA-256 ہیش جنریٹر", "ہیش", "چیک سم",
        "SHA-256 हैश जनरेटर", "हैश", "चेकसम"
      )
    ),

    // Tool 8: ZIP Creator
    Tool(
      id = 8,
      category = Category.FILES,
      phase = 3,
      nameRes = R.string.tool8_name,
      descRes = R.string.tool8_desc,
      icon = Icons.Default.FolderZip,
      defaultName = "ZIP Creator",
      keywords = listOf(
        "zip creator", "zip", "archive", "compress files", "compress folder", "zip multiple",
        "ZIP بنانے والا", "زپ فائل", "آرکائیو", "فولڈر کمپریس",
        "ज़िप क्रिएटर", "ज़िप फ़ाइल", "संग्रह", "फ़ोल्डर कंप्रेस"
      )
    ),

    // Tool 9: QR Code Generator
    Tool(
      id = 9,
      category = Category.FILES,
      phase = 4,
      nameRes = R.string.tool9_name,
      descRes = R.string.tool9_desc,
      icon = Icons.Default.QrCode,
      defaultName = "QR Code Generator",
      keywords = listOf(
        "qr code generator", "qr", "barcode", "generate qr", "link to qr", "text to qr",
        "کیو آر کوڈ جنریٹر", "کیو آر",
        "क्यूआर कोड जनरेटर", "क्यूआर कोड"
      )
    ),

    // Tool 10: PDF Merge
    Tool(
      id = 10,
      category = Category.PDF,
      phase = 4,
      nameRes = R.string.tool10_name,
      descRes = R.string.tool10_desc,
      icon = Icons.Default.CallMerge,
      defaultName = "PDF Merge",
      keywords = listOf(
        "pdf merge", "merge pdf", "combine pdf", "join pdf", "merge documents",
        "پی ڈی ایف ضم کنندہ", "پی ڈی ایف جوڑیں", "ضم",
        "पीडीएफ मर्ज", "पीडीएफ जोड़ें", "मर्ज"
      )
    ),

    // Tool 11: PDF Split
    Tool(
      id = 11,
      category = Category.PDF,
      phase = 4,
      nameRes = R.string.tool11_name,
      descRes = R.string.tool11_desc,
      icon = Icons.Default.CallSplit,
      defaultName = "PDF Split",
      keywords = listOf(
        "pdf split", "split pdf", "extract pages", "separate pdf", "pages",
        "پی ڈی ایف تقسیم کنندہ", "صفحات الگ کریں", "تقسیم",
        "पीडीएफ स्प्लिट", "पृष्ठ अलग करें", "विभाजित"
      )
    ),

    // Tool 12: PDF Compressor
    Tool(
      id = 12,
      category = Category.PDF,
      phase = 5,
      nameRes = R.string.tool12_name,
      descRes = R.string.tool12_desc,
      icon = Icons.Default.Compress,
      defaultName = "PDF Compressor",
      keywords = listOf(
        "pdf compressor", "compress pdf", "reduce pdf size", "shrink pdf", "reduce pdf", "optimize pdf",
        "پی ڈی ایف کمپریسر", "پی ڈی ایف سائز کم", "پی ڈی ایف سائز گھٹائیں",
        "पीडीएफ कंप्रेसर", "पीडीएफ आकार कम", "पीडीएफ साइज कम"
      )
    ),

    // Tool 13: PDF to Images
    Tool(
      id = 13,
      category = Category.PDF,
      phase = 5,
      nameRes = R.string.tool13_name,
      descRes = R.string.tool13_desc,
      icon = Icons.Default.Collections,
      defaultName = "PDF to Images",
      keywords = listOf(
        "pdf to images", "pdf to image", "pdf to jpg", "pdf to png", "pages to images", "pdf extract images",
        "پی ڈی ایف سے تصاویر", "پی ڈی ایف تصویر", "پی ڈی ایف ٹو امیج",
        "पीडीएफ से छवियां", "पीडीएफ फोटो", "पीडीएफ से जेपीजी"
      )
    ),

    // Tool 14: Images to PDF
    Tool(
      id = 14,
      category = Category.PDF,
      phase = 5,
      nameRes = R.string.tool14_name,
      descRes = R.string.tool14_desc,
      icon = Icons.Default.PictureAsPdf,
      defaultName = "Images to PDF",
      keywords = listOf(
        "images to pdf", "image to pdf", "jpg to pdf", "png to pdf", "photos to pdf", "make pdf from images", "pictures to pdf",
        "تصاویر سے پی ڈی ایف", "تصویر پی ڈی ایف", "جے پی جی سے پی ڈی ایف",
        "छवियों से पीडीएफ", "फोटो से पीडीएफ", "जेपीजी से पीडीएफ"
      )
    ),

    // Tool 15: Rotate PDF
    Tool(
      id = 15,
      category = Category.PDF,
      phase = 4,
      nameRes = R.string.tool15_name,
      descRes = R.string.tool15_desc,
      icon = Icons.Default.RotateRight,
      defaultName = "Rotate PDF",
      keywords = listOf(
        "rotate pdf", "turn pdf", "pdf orientation", "rotate pages", "degrees",
        "پی ڈی ایف گھمائیں", "رخ تبدیل کریں",
        "पीडीएफ घुमाएं", "दिशा बदलें"
      )
    ),

    // Tool 16: Video to Audio
    Tool(
      id = 16,
      category = Category.VIDEO_AUDIO,
      phase = 6,
      nameRes = R.string.tool16_name,
      descRes = R.string.tool16_desc,
      icon = Icons.Default.Audiotrack,
      defaultName = "Video to Audio",
      keywords = listOf(
        "video to audio", "extract audio", "mp4 to mp3", "video audio", "sound",
        "ویڈیو سے آڈیو", "آڈیو نکالیں", "آواز",
        "वीडियो से ऑडियो", "ऑडियो निकालें", "ध्वनि"
      )
    ),

    // Tool 17: Audio Converter
    Tool(
      id = 17,
      category = Category.VIDEO_AUDIO,
      phase = 6,
      nameRes = R.string.tool17_name,
      descRes = R.string.tool17_desc,
      icon = Icons.Default.GraphicEq,
      defaultName = "Audio Converter",
      keywords = listOf(
        "audio converter", "convert audio", "mp3", "wav", "m4a", "aac", "audio format",
        "آڈیو کنورٹر", "آڈیو فارمیٹ تبدیل",
        "ऑडियो कनवर्टर", "ऑडियो प्रारूप"
      )
    ),

    // Tool 18: Video Compressor
    Tool(
      id = 18,
      category = Category.VIDEO_AUDIO,
      phase = 6,
      nameRes = R.string.tool18_name,
      descRes = R.string.tool18_desc,
      icon = Icons.Default.VideoFile,
      defaultName = "Video Compressor",
      keywords = listOf(
        "video compressor", "compress video", "shrink video", "bitrate", "reduce video size",
        "ویڈیو کمپریسر", "ویڈیو سائز کم کریں",
        "वीडियो कंप्रेसर", "वीडियो आकार कम"
      )
    ),

    // Tool 19: Audio Trimmer
    Tool(
      id = 19,
      category = Category.VIDEO_AUDIO,
      phase = 6,
      nameRes = R.string.tool19_name,
      descRes = R.string.tool19_desc,
      icon = Icons.Default.ContentCut,
      defaultName = "Audio Trimmer",
      keywords = listOf(
        "audio trimmer", "trim audio", "cut audio", "ringtone", "audio cutter",
        "آڈیو ٹرمر", "آڈیو کاٹیں", "رنگ ٹون",
        "ऑडियो ट्रिमर", "ऑडियो काटें", "रिंगटोन"
      )
    ),

    // Tool 20: AI Background Remover
    Tool(
      id = 20,
      category = Category.IMAGE,
      phase = 7,
      nameRes = R.string.tool20_name,
      descRes = R.string.tool20_desc,
      icon = Icons.Default.AutoFixHigh,
      defaultName = "AI Background Remover",
      keywords = listOf(
        "ai background remover", "remove background", "bg remover", "cutout", "cut out", "transparent background", "transparent", "png", "isolate subject",
        "اے آئی پس منظر ہٹانے والا", "پس منظر ہٹائیں", "بیک گراؤنڈ", "شفاف پس منظر", "شفاف", "کٹ آؤٹ", "پی این جی",
        "एआई बैकग्राउंड रिमूवर", "बैकग्राउंड हटाएं", "कटआउट", "पारदर्शी बैकग्राउंड", "पारदर्शी", "पीएनजी", "कट आउट"
      )
    ),

    // Tool 21: PDF Editor
    Tool(
      id = 21,
      category = Category.PDF,
      phase = 8,
      nameRes = R.string.tool21_name,
      descRes = R.string.tool21_desc,
      icon = Icons.Default.EditNote,
      defaultName = "PDF Editor",
      keywords = listOf(
        "pdf editor", "edit pdf", "add text to pdf", "sign pdf", "signature", "annotate", "highlight", "whiteout", "redact", "fill form", "pdf edit",
        "پی ڈی ایف ایڈیٹر", "پی ڈی ایف ایڈٹ", "دستخط", "پی ڈی ایف لکھیں",
        "पीडीएफ एडिटर", "पीडीएफ संपादित करें", "हस्ताक्षर", "पीडीएफ एडिट"
      )
    ),

    // Tool 22: AI Image Inspector
    Tool(
      id = 22,
      category = Category.IMAGE,
      phase = 8,
      nameRes = R.string.tool22_name,
      descRes = R.string.tool22_desc,
      icon = Icons.Default.ImageSearch,
      defaultName = "AI Image Inspector",
      keywords = listOf(
        "ai image inspector", "ai image", "ai detector", "fake image", "deepfake", "is this ai", "c2pa", "content credentials", "midjourney", "stable diffusion", "dall-e", "firefly", "gemini", "image forensics", "ela",
        "اے آئی تصویر معائنہ", "جعلی تصویر", "اے آئی شناخت",
        "एआई छवि निरीक्षक", "नकली छवि", "एआई डिटेक्टर", "डीपफेक"
      )
    )
  )

  fun getById(id: Int): Tool? = tools.find { it.id == id }

  fun getByCategory(category: Category): List<Tool> = tools.filter { it.category == category }
}
