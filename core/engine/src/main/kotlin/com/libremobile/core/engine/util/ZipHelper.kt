package com.libremobile.core.engine.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipHelper {

    fun readZip(inputStream: InputStream): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(inputStream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val buffer = ByteArrayOutputStream()
                    val data = ByteArray(4096)
                    var count: Int
                    while (zis.read(data).also { count = it } != -1) {
                        buffer.write(data, 0, count)
                    }
                    entries[entry.name] = buffer.toByteArray()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return entries
    }

    fun writeZip(entries: Map<String, ByteArray>, outputStream: OutputStream) {
        ZipOutputStream(outputStream).use { zos ->
            for ((name, data) in entries) {
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(data)
                zos.closeEntry()
            }
        }
    }

    /**
     * ODF specification requires the 'mimetype' file to be uncompressed (STORED)
     * as the first entry in the zip package without any extra field.
     */
    fun writeOdfZip(mimetype: String, entries: Map<String, ByteArray>, outputStream: OutputStream) {
        ZipOutputStream(outputStream).use { zos ->
            val mimeBytes = mimetype.toByteArray(Charsets.US_ASCII)
            val mimeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimeBytes.size.toLong()
                compressedSize = mimeBytes.size.toLong()
                val crc = CRC32()
                crc.update(mimeBytes)
                setCrc(crc.value)
            }
            zos.putNextEntry(mimeEntry)
            zos.write(mimeBytes)
            zos.closeEntry()

            for ((name, data) in entries) {
                if (name == "mimetype") continue
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(data)
                zos.closeEntry()
            }
        }
    }
}
