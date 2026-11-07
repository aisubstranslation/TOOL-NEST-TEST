package com.example.tools.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadBuildersTest {

  @Test
  fun testUrlPayload() {
    val (urlWithScheme, prepended1) = QrPayloadBuilders.buildUrlPayload("example.com")
    assertEquals("https://example.com", urlWithScheme)
    assertTrue(prepended1)

    val (existingScheme, prepended2) = QrPayloadBuilders.buildUrlPayload("http://toolsnest.org")
    assertEquals("http://toolsnest.org", existingScheme)
    assertFalse(prepended2)
  }

  @Test
  fun testWifiPayloadEscaping() {
    val payload = QrPayloadBuilders.buildWifiPayload(
      ssid = "My;Special\\Network",
      password = "secret:pass,word",
      security = "WPA",
      hidden = true
    )
    assertEquals("WIFI:T:WPA;S:My\\;Special\\\\Network;P:secret\\:pass\\,word;H:true;;", payload)
  }

  @Test
  fun testWifiPayloadNoPass() {
    val payload = QrPayloadBuilders.buildWifiPayload(
      ssid = "OpenNet",
      password = "",
      security = "nopass",
      hidden = false
    )
    assertEquals("WIFI:T:nopass;S:OpenNet;P:;H:false;;", payload)
  }

  @Test
  fun testVCardPayload() {
    val vcard = QrPayloadBuilders.buildVCardPayload(
      name = "John Doe",
      phone = "+1234567890",
      email = "john@example.com",
      organization = "Acme, Inc.",
      url = "example.com"
    )
    assertTrue(vcard.startsWith("BEGIN:VCARD"))
    assertTrue(vcard.contains("FN:John Doe"))
    assertTrue(vcard.contains("TEL;TYPE=CELL:+1234567890"))
    assertTrue(vcard.contains("EMAIL:john@example.com"))
    assertTrue(vcard.contains("ORG:Acme\\, Inc."))
    assertTrue(vcard.contains("URL:https://example.com"))
    assertTrue(vcard.endsWith("END:VCARD"))
  }

  @Test
  fun testEmailPayload() {
    val simple = QrPayloadBuilders.buildEmailPayload("support@toolsnest.org")
    assertEquals("mailto:support@toolsnest.org", simple)

    val withSubjectAndBody = QrPayloadBuilders.buildEmailPayload(
      "support@toolsnest.org",
      subject = "Hello World",
      body = "First line"
    )
    assertEquals("mailto:support@toolsnest.org?subject=Hello%20World&body=First%20line", withSubjectAndBody)
  }

  @Test
  fun testPhonePayload() {
    val phone = QrPayloadBuilders.buildPhonePayload("+1 234 567 890")
    assertEquals("tel:+1234567890", phone)
  }

  @Test
  fun testInvertedQrVerification() {
    val payload = "Welcome to TOOLSNEST"
    val bitMatrix = QrCodeGenerator.encodePayloadToBitMatrix(payload)
    val (width, height, pixels) = QrCodeGenerator.generateQrPixels(
      bitMatrix = bitMatrix,
      targetDimensionPx = 512,
      foregroundColor = 0xFFFFFFFF.toInt(), // White code
      backgroundColor = 0xFF000000.toInt()  // Black background
    )

    val (isVerified, decoded) = QrCodeGenerator.decodePixels(pixels, width, height)
    assertTrue("Inverted QR code must be verified by decodePixels fallback", isVerified)
    assertEquals(payload, decoded)
  }
}
