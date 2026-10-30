package com.raju.aiassistant.data.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The hand-written v2 → v3 migration must create exactly the tables and indices Room expects, or
 * Room refuses to open the upgraded database. Room writes its expected SQL to the exported schema
 * during compilation; this compares the two.
 */
class RajuDatabaseSchemaTest {
    @Test
    fun migration2to3MatchesRoomsSchema() {
        // The khata and PNR tables are unchanged since v3, so the current (v4) schema is the reference.
        assertEquals(expectedSql(4, setOf("expenses", "journeys")).sorted(), RajuDatabase.MIGRATION_2_3_SQL.sorted())
    }

    @Test
    fun migration3to4MatchesRoomsSchema() {
        assertEquals(expectedSql(4, setOf("kb_documents", "kb_chunks")).sorted(), RajuDatabase.MIGRATION_3_4_SQL.sorted())
    }

    private fun expectedSql(version: Int, tables: Set<String>): List<String> {
        val file = File("schemas/com.raju.aiassistant.data.db.RajuDatabase/$version.json")
        assertTrue("Room schema not exported at ${file.absolutePath}", file.exists())
        val db = Json.parseToJsonElement(file.readText()) as JsonObject
        val expected = mutableListOf<String>()
        for (e in ((db["database"] as JsonObject)["entities"] as JsonArray).map { it as JsonObject }) {
            val table = e["tableName"]!!.jsonPrimitive.content
            if (table !in tables) continue
            expected += e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)
            (e["indices"] as? JsonArray)?.forEach { idx ->
                expected += (idx as JsonObject)["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)
            }
        }
        return expected
    }
}
