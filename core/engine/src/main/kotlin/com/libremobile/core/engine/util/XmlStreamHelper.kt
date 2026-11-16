package com.libremobile.core.engine.util

import java.io.InputStream
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource

object XmlStreamHelper {

    private val dbFactory by lazy {
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
    }

    fun parseXml(bytes: ByteArray): Document {
        val builder = dbFactory.newDocumentBuilder()
        return builder.parse(InputSource(bytes.inputStream()))
    }

    fun parseXml(xmlString: String): Document {
        val builder = dbFactory.newDocumentBuilder()
        return builder.parse(InputSource(StringReader(xmlString)))
    }

    fun NodeList.forEachElement(action: (Element) -> Unit) {
        for (i in 0 until length) {
            val node = item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                action(node as Element)
            }
        }
    }

    fun Element.findChildElements(localName: String): List<Element> {
        val result = mutableListOf<Element>()
        val children = childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val elem = node as Element
                if (elem.localName == localName || elem.tagName.substringAfter(":") == localName) {
                    result.add(elem)
                }
            }
        }
        return result
    }

    fun Element.findFirstChildElement(localName: String): Element? {
        val children = childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val elem = node as Element
                if (elem.localName == localName || elem.tagName.substringAfter(":") == localName) {
                    return elem
                }
            }
        }
        return null
    }

    fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
