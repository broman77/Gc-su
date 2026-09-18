package ru.su555.defects

import android.content.Context

object ManagementSettings {
    private const val PREFS = "management_settings"

    fun load(context: Context): LaborNorms {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return LaborNorms(
            defaultHours = p.getFloat("default_hours", 1.0f).toDouble(),
            electricalPlumbingHours = p.getFloat("electrical_hours", 1.5f).toDouble(),
            finishingHours = p.getFloat("finishing_hours", 1.75f).toDouble(),
            tileHours = p.getFloat("tile_hours", 2.0f).toDouble(),
            ceilingHours = p.getFloat("ceiling_hours", 2.0f).toDouble(),
            glassHours = p.getFloat("glass_hours", 2.5f).toDouble(),
            doorsFloorHours = p.getFloat("doors_floor_hours", 1.25f).toDouble(),
            targetWorkingDays = p.getInt("target_days", 10).coerceIn(1, 60)
        )
    }

    fun crewSize(context: Context, contractor: String): Int {
        val key = "crew_" + contractor.lowercase().replace(Regex("[^а-яa-z0-9]+"), "_")
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(key, 0)
            .coerceAtLeast(0)
    }

    fun saveCrewSize(context: Context, contractor: String, size: Int) {
        val key = "crew_" + contractor.lowercase().replace(Regex("[^а-яa-z0-9]+"), "_")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(key, size.coerceAtLeast(0))
            .apply()
    }

    fun save(context: Context, norms: LaborNorms) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat("default_hours", norms.defaultHours.toFloat())
            .putFloat("electrical_hours", norms.electricalPlumbingHours.toFloat())
            .putFloat("finishing_hours", norms.finishingHours.toFloat())
            .putFloat("tile_hours", norms.tileHours.toFloat())
            .putFloat("ceiling_hours", norms.ceilingHours.toFloat())
            .putFloat("glass_hours", norms.glassHours.toFloat())
            .putFloat("doors_floor_hours", norms.doorsFloorHours.toFloat())
            .putInt("target_days", norms.targetWorkingDays.coerceIn(1, 60))
            .apply()
    }
}
