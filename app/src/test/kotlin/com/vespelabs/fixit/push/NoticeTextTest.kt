package com.vespelabs.fixit.push

import com.vespelabs.fixit.R
import com.vespelabs.fixit.core.data.push.PushEventType
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec "El aviso visible no filtra datos personales": the text depends on the event type only (design 14b.5). */
class NoticeTextTest {
    /** The values of :app strings.xml (unit tests run with the module directory as working directory). */
    private val strings: Map<String, String> by lazy {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values/strings.xml"))
        val nodes = document.getElementsByTagName("string")
        (0 until nodes.length).associate { i ->
            val node = nodes.item(i)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    private fun nameOf(id: Int): String = R.string::class.java.fields.first { it.getInt(null) == id }.name

    private fun textOf(id: Int): String = strings.getValue(nameOf(id))

    @Test
    fun `El aviso visible no filtra datos personales - each type has its own fixed text from the app strings`() {
        assertEquals("Un profesional aceptó tu solicitud", textOf(noticeTextFor(PushEventType.REQUEST_ACCEPTED).body))
        assertEquals("Hay una nueva solicitud cerca", textOf(noticeTextFor(PushEventType.NEW_REQUEST).body))
        assertEquals("El cliente canceló un trabajo aceptado", textOf(noticeTextFor(PushEventType.REQUEST_CANCELLED).body))
    }

    @Test
    fun `given the three types then the texts are distinct and none has a placeholder for request or person data`() {
        val texts = PushEventType.entries.map(::noticeTextFor)

        assertEquals(3, texts.map { it.body }.toSet().size)
        for (text in texts) {
            assertEquals("FixIt", textOf(text.title))
            for (value in listOf(textOf(text.title), textOf(text.body))) {
                assertEquals("no format argument in \"$value\"", false, value.contains('%'))
            }
        }
    }

    @Test
    fun `given the notification channel then its name is a fixed app string`() {
        assertEquals("Avisos de solicitudes", textOf(R.string.notification_channel_requests))
    }
}
