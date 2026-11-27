package org.cartavelio.docx2hub

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.nio.file.Path
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.readBytes

object Xml {
    const val WORD = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    const val DOCBOOK = "http://docbook.org/ns/docbook"
    const val IMPORTER = "http://transpect.io/docx2hub"
    const val XSL = "http://www.w3.org/1999/XSL/Transform"

    fun parse(path: Path): Document = parse(path.readBytes().toString(Charsets.UTF_8))

    fun parse(xml: String): Document {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
    }

    fun elements(document: Document): List<Element> {
        val nodes = document.getElementsByTagName("*")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    fun attributes(element: Element): Map<String, String> =
        (0 until element.attributes.length)
            .map { element.attributes.item(it) }
            .filter { it.namespaceURI != XMLConstants.XMLNS_ATTRIBUTE_NS_URI }
            .associate { it.nodeName to it.nodeValue }

    fun escape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")

    /** Independent DOM oracle: count lexical nodeName per parent, never expanded QName. */
    fun lexicalLocations(xml: String): List<Pair<Element, String>> {
        val result = mutableListOf<Pair<Element, String>>()
        visit(parse(xml), "", result)
        return result
    }

    private fun visit(
        node: Node,
        parent: String,
        result: MutableList<Pair<Element, String>>,
    ) {
        val counts = mutableMapOf<String, Int>()
        for (index in 0 until node.childNodes.length) {
            val child = node.childNodes.item(index)
            if (child is Element) {
                val position = counts.getOrDefault(child.nodeName, 0) + 1
                counts[child.nodeName] = position
                val path = "$parent/${child.nodeName}[$position]"
                result.add(child to path)
                visit(child, path, result)
            }
        }
    }
}
