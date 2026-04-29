package io.github.ranzlappen.synthpiano.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * User-defined keyboard layouts are persisted as a single JSON-serialised
 * array under [PreferencesRepository.userLayoutsJson]. Built-ins live in
 * [BuiltInLayouts] and never touch DataStore.
 *
 * Mirrors [PresetRepository]: `userLayouts` flow + save/rename/delete by
 * name + an `apply` helper that pushes a layout into the active-layout slot.
 */
class LayoutRepository(private val prefs: PreferencesRepository) {

    val userLayouts: Flow<List<KeyboardLayout>> = prefs.userLayoutsJson.map { raw ->
        if (raw.isNullOrBlank()) emptyList()
        else parseKeyboardLayoutListJson(raw).map { it.copy(builtin = false) }
    }

    suspend fun saveUser(layout: KeyboardLayout) {
        val name = layout.name.trim()
        if (name.isEmpty()) return
        // Names must not collide with built-ins.
        if (BuiltInLayouts.ALL.any { it.name == name }) return
        val current = userLayouts.first().toMutableList()
        val replacement = layout.copy(name = name, builtin = false)
        val idx = current.indexOfFirst { it.name == name }
        if (idx >= 0) current[idx] = replacement else current.add(replacement)
        writeAll(current)
    }

    suspend fun renameUser(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == oldName) return
        if (BuiltInLayouts.ALL.any { it.name == trimmed }) return
        val current = userLayouts.first().toMutableList()
        val idx = current.indexOfFirst { it.name == oldName }
        if (idx < 0) return
        if (current.any { it.name == trimmed && it.name != oldName }) return
        current[idx] = current[idx].copy(name = trimmed)
        writeAll(current)
    }

    suspend fun deleteUser(name: String) {
        val current = userLayouts.first().filter { it.name != name }
        writeAll(current)
    }

    /** Push a layout into the active-layout slot. */
    suspend fun apply(layout: KeyboardLayout) {
        prefs.setKeyboardLayout(layout)
    }

    private suspend fun writeAll(list: List<KeyboardLayout>) {
        prefs.setUserLayoutsJson(list.toLayoutListJson())
    }
}
