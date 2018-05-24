package pl.zyper.musiccontrol

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.preference.EditTextPreference
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.PreferenceGroup
import android.support.v4.app.NavUtils
import android.view.MenuItem

class SettingsActivity : AppCompatPreferenceActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.SettingsTheme)
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        fragmentManager.beginTransaction().replace(
                android.R.id.content,
                SettingsFragment()
        ).commit()

    }

    override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            if (!super.onMenuItemSelected(featureId, item)) {
                NavUtils.navigateUpFromSameTask(this)
            }
            return true
        }
        return super.onMenuItemSelected(featureId, item)
    }

    override fun onIsMultiPane(): Boolean {
        return isXLargeTablet(this)
    }


    override fun isValidFragment(fragmentName: String): Boolean {
        return PreferenceFragment::class.java.name == fragmentName
    }

    companion object {
        class SettingsFragment : PreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                addPreferencesFromResource(R.xml.preferences)
            }

            override fun onResume() {
                super.onResume()
                this.preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
                updateAllPreferences()
            }

            override fun onPause() {
                super.onPause()
                this.preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
            }

            private fun updateAllPreferences() {
                for (i in 0 until preferenceScreen.preferenceCount) {
                    updatePreference(preferenceScreen.getPreference(i))
                }
            }

            override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
                val preference = findPreference(key)
                System.out.println("changed $key to $preference")
                updatePreference(preference)

            }

            private fun updatePreference(preference: Preference) {
                if (preference is PreferenceGroup) {
                    for (i in 0 until preference.preferenceCount) {
                        updatePreference(preference.getPreference(i))
                    }
                } else {
                    if (preference is EditTextPreference) {
                        with(preference) {
                            preference.summary = preference.text
                        }
                    }
                }

            }
        }

        private fun isXLargeTablet(context: Context): Boolean {
            return context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >= Configuration.SCREENLAYOUT_SIZE_XLARGE
        }


        fun start(context: Context) {
            context.startActivity(Intent(context, SettingsActivity::class.java))
        }
    }
}
