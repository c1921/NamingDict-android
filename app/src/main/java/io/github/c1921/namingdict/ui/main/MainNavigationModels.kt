package io.github.c1921.namingdict.ui

import io.github.c1921.namingdict.R

internal enum class MainTab(val titleResId: Int) {
    Filter(R.string.tab_filter),
    Dictionary(R.string.tab_dictionary),
    NewFeature(R.string.tab_new_feature),
    Settings(R.string.tab_settings)
}

internal enum class SettingsPage(val titleResId: Int) {
    Home(R.string.tab_settings),
    BackupRestore(R.string.settings_backup_restore_title),
    About(R.string.settings_about_title)
}
