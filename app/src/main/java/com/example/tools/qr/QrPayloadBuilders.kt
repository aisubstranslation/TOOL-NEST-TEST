package com.example.tools.qr

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object QrPayloadBuilders {

  /**
   * Prepares a URL string. If no scheme is present, prepends "https://" and flags it.
   */
  fun buildUrlPayload(rawUrl: String): Pair<String, Boolean> {
    val trimmed = rawUrl.trim()
    return if (trimmed.contains("://")) {
      trimmed to false
    } else {
      "https://$trimmed" to true
    }
  }

  /**
   * Escapes special characters (\, ;, ,, :, ") for Wi-Fi QR code syntax.
   */
  fun escapeWifi(value: String): String {
    val sb = StringBuilder()
    for (ch in value) {
      if (ch == '\\' || ch == ';' || ch == ',' || ch == ':' || ch == '"') {
        sb.append('\\')
      }
      sb.append(ch)
    }
    return sb.toString()
  }

  /**
   * Builds standard Wi-Fi configuration string:
   * WIFI:T:<type>;S:<ssid>;P:<password>;H:<hidden>;;
   * Security options: "WPA", "WEP", "nopass"
   */
  fun buildWifiPayload(
    ssid: String,
    password: String,
    security: String,
    hidden: Boolean = false
  ): String {
    val secType = when (security.uppercase()) {
      "WEP" -> "WEP"
      "NONE", "NOPASS" -> "nopass"
      else -> "WPA"
    }
    val escapedSsid = escapeWifi(ssid.trim())
    val escapedPass = if (secType != "nopass") escapeWifi(password) else ""
    val hiddenFlag = if (hidden) "true" else "false"

    return "WIFI:T:$secType;S:$escapedSsid;P:$escapedPass;H:$hiddenFlag;;"
  }

  /**
   * Builds vCard 3.0 contact payload with safe line escaping.
   */
  fun buildVCardPayload(
    name: String,
    phone: String = "",
    email: String = "",
    organization: String = "",
    url: String = ""
  ): String {
    fun escapeVCard(str: String): String {
      return str.replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")
    }

    return buildString {
      appendLine("BEGIN:VCARD")
      appendLine("VERSION:3.0")
      val escName = escapeVCard(name.trim())
      appendLine("N:;$escName;;;")
      appendLine("FN:$escName")
      if (phone.isNotBlank()) {
        appendLine("TEL;TYPE=CELL:${phone.trim()}")
      }
      if (email.isNotBlank()) {
        appendLine("EMAIL:${email.trim()}")
      }
      if (organization.isNotBlank()) {
        appendLine("ORG:${escapeVCard(organization.trim())}")
      }
      if (url.isNotBlank()) {
        val (finalUrl, _) = buildUrlPayload(url)
        appendLine("URL:$finalUrl")
      }
      append("END:VCARD")
    }
  }

  /**
   * Builds mailto: string.
   */
  fun buildEmailPayload(email: String, subject: String = "", body: String = ""): String {
    val mail = email.trim()
    val params = mutableListOf<String>()
    if (subject.isNotBlank()) {
      params.add("subject=" + URLEncoder.encode(subject, StandardCharsets.UTF_8.name()).replace("+", "%20"))
    }
    if (body.isNotBlank()) {
      params.add("body=" + URLEncoder.encode(body, StandardCharsets.UTF_8.name()).replace("+", "%20"))
    }
    return if (params.isEmpty()) {
      "mailto:$mail"
    } else {
      "mailto:$mail?${params.joinToString("&")}"
    }
  }

  /**
   * Builds tel: string.
   */
  fun buildPhonePayload(phone: String): String {
    val cleaned = phone.trim().replace(" ", "")
    return "tel:$cleaned"
  }
}
