package io.github.boarnasia.cli.seed

import kotlinx.serialization.Serializable

@Serializable
data class SeedCreatedJson(val id: String, val displayNumber: String, val publicId: String?)

@Serializable
data class SeedModuleJson(val module: String, val created: List<SeedCreatedJson>)

@Serializable
data class SeedJson(val modules: List<SeedModuleJson>) {
    companion object {
        fun of(results: List<SeedModuleResult>) = SeedJson(
            results.map { r ->
                SeedModuleJson(r.module.key, r.created.map { SeedCreatedJson(it.id.toString(), it.displayNumber.toString(), it.publicId?.toString()) })
            },
        )

        fun human(data: SeedJson): String = data.modules.joinToString("\n") { m ->
            val range = when (m.created.size) {
                0 -> ""
                1 -> "（${m.created.single().displayNumber}）"
                else -> "（${m.created.first().displayNumber}〜${m.created.last().displayNumber}）"
            }
            "${m.module}: ${m.created.size} 件$range"
        }
    }
}
