import io.github.dellisd.spatialk.geojson.FeatureCollection
import io.github.dellisd.spatialk.geojson.Point
import java.io.InputStream
import kotlin.collections.ArrayList

const val regionalschluessel = "055150000000" // Münster

private data class KatasterTree(val tags: Map<String, String>, val isHPA: Boolean)

fun parseKataster(inputStream: InputStream, dataTimeStamp: String): List<OsmNode> {
    val features =
            FeatureCollection.fromJson(json = inputStream.bufferedReader().use { it.readText() })

    val trees = ArrayList<OsmNode>()

    for (feature in features.features) {
        if (feature.geometry == null || feature.geometry !is Point) {
            continue // skip features without correct geometry
        }

        val streetKey = feature.getStringProperty("str_schl")
        val treeType = feature.getStringProperty("baumgruppe")
        if (streetKey == null || treeType == null) {
            continue // skip features without str_schluessel or baumgruppe
        }

        val point = feature.geometry as Point
        val tags =
                hashMapOf(
                        "natural" to "tree",
                        "de:strassenschluessel" to "${regionalschluessel}${streetKey}"
                )

        if (treeType.length > 0) {
            tags["species:de"] = treeType
        }

        val osm =
                OsmNode(
                        id = -1L,
                        version = 1,
                        timestamp = dataTimeStamp,
                        position = LatLon(point.coordinates.latitude, point.coordinates.longitude),
                        tags = tags
                )

        trees.add(osm)
    }

    return trees
}

fun assignStableTreeIds(features: FeatureCollection) {
    // Group features by street key
    val grouped = features.features.groupBy { it.getStringProperty("str_schl") }
    for ((streetKey, group) in grouped) {
        if (streetKey == null) continue
        // Sort by baumgruppe and then by coordinates for deterministic order
        val sorted =
                group.sortedWith(
                        compareBy(
                                { it.getStringProperty("baumgruppe") ?: "unknown" },
                                { (it.geometry as? Point)?.coordinates?.latitude ?: 0.0 },
                                { (it.geometry as? Point)?.coordinates?.longitude ?: 0.0 }
                        )
                )
        for ((index, feature) in sorted.withIndex()) {
            // Compose a stable string from streetKey, baumgruppe, and index
            val treeType = feature.getStringProperty("baumgruppe") ?: "unknown"
            val stableString = "${streetKey}_${treeType}_$index"
            feature.setStringProperty("kataster_id", stableString)
        }
    }
}
