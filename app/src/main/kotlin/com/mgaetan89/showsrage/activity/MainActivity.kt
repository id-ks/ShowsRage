package com.mgaetan89.showsrage.activity

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.Settings
import android.support.design.widget.AppBarLayout
import android.support.design.widget.NavigationView
import android.support.design.widget.TabLayout
import android.support.v4.app.Fragment
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.graphics.ColorUtils
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.app.AppCompatDelegate
import android.support.v7.graphics.Palette
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.mgaetan89.showsrage.Constants
import com.mgaetan89.showsrage.R
import com.mgaetan89.showsrage.ShowsRageApplication
import com.mgaetan89.showsrage.fragment.*
import com.mgaetan89.showsrage.helper.ShowsRageReceiver
import com.mgaetan89.showsrage.helper.Utils
import com.mgaetan89.showsrage.model.GenericResponse
import com.mgaetan89.showsrage.model.RootDirs
import com.mgaetan89.showsrage.model.UpdateResponse
import com.mgaetan89.showsrage.model.UpdateResponseWrapper
import com.mgaetan89.showsrage.network.SickRageApi
import com.mgaetan89.showsrage.view.ColoredToolbar
import retrofit.Callback
import retrofit.RetrofitError
import retrofit.client.Response
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity(), Callback<GenericResponse>, NavigationView.OnNavigationItemSelectedListener {
    private val COLOR_DARK_FACTOR = 0.8f

    private var appBarLayout: AppBarLayout? = null
    private var drawerHeader: LinearLayout? = null
    private var drawerLayout: DrawerLayout? = null
    private var drawerToggle: ActionBarDrawerToggle? = null
    private var navigationView: NavigationView? = null
    private val receiver = ShowsRageReceiver(this)
    private var tabLayout: TabLayout? = null
    private var toolbar: ColoredToolbar? = null

    fun displayHomeAsUp(displayHomeAsUp: Boolean) {
        val actionBar = this.supportActionBar

        if (displayHomeAsUp) {
            this.drawerToggle?.isDrawerIndicatorEnabled = false
            actionBar?.setDisplayHomeAsUpEnabled(true)
        } else {
            actionBar?.setDisplayHomeAsUpEnabled(false)
            this.drawerToggle?.isDrawerIndicatorEnabled = true
        }
    }

    override fun failure(error: RetrofitError?) {
        error?.printStackTrace()
    }

    override fun onBackPressed() {
        if (this.navigationView != null && this.drawerLayout?.isDrawerOpen(this.navigationView) ?: false) {
            this.drawerLayout?.closeDrawers()
        } else {
            super.onBackPressed()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)

        this.drawerToggle?.onConfigurationChanged(newConfig)
    }

    override fun onNavigationItemSelected(item: MenuItem?): Boolean {
        var eventHandled = true
        var fragment: Fragment? = null
        var id = item?.itemId

        when (id) {
            R.id.menu_check_update -> {
                eventHandled = false

                this.checkForUpdate(true)
            }

            R.id.menu_history -> fragment = HistoryFragment()
            R.id.menu_logs -> fragment = LogsFragment()

            R.id.menu_post_processing -> {
                eventHandled = false

                PostProcessingFragment().show(this.supportFragmentManager, "post_processing")
            }

            R.id.menu_remote_control -> {
                eventHandled = false

                RemoteControlFragment().show(this.supportFragmentManager, "remote_control")
            }

            R.id.menu_restart -> {
                eventHandled = false

                AlertDialog.Builder(this)
                        .setMessage(R.string.restart_confirm)
                        .setPositiveButton(R.string.restart, { dialog, which ->
                            SickRageApi.instance.services?.restart(this)
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
            }

            R.id.menu_schedule -> fragment = ScheduleFragment()

            R.id.menu_settings -> {
                val settingsFragment = SettingsFragment()

                this.removeCurrentSupportFragment()

                this.fragmentManager.beginTransaction()
                        .replace(R.id.content, settingsFragment)
                        .commit()
            }

            R.id.menu_shows -> fragment = ShowsFragment()

            R.id.menu_statistics -> {
                eventHandled = false

                StatisticsFragment().show(this.supportFragmentManager, "statistics")
            }
        }

        if (this.navigationView != null) {
            this.drawerLayout?.closeDrawer(this.navigationView)
        }

        if (eventHandled) {
            item?.isChecked = true

            this.tabLayout?.removeAllTabs()
            this.tabLayout?.visibility = View.GONE
        }

        if (fragment != null) {
            this.removeCurrentFragment()
            this.resetThemeColors()

            this.toolbar?.menu?.clear()

            this.supportFragmentManager.beginTransaction()
                    .replace(R.id.content, fragment)
                    .commit()
        }

        return eventHandled
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (this.drawerToggle?.onOptionsItemSelected(item) ?: false) {
            return true
        }

        if (item?.itemId == android.R.id.home) {
            this.onBackPressed()

            return true
        }

        return super.onOptionsItemSelected(item)
    }

    fun setPalette(palette: Palette) {
        var accent = palette.darkMutedSwatch
        var accentColor: Int
        var primary = palette.vibrantSwatch
        var primaryColor: Int

        if (accent == null) {
            accent = palette.mutedSwatch

            if (accent == null) {
                accent = palette.lightMutedSwatch
            }
        }

        if (accent == null) {
            accentColor = ContextCompat.getColor(this, R.color.accent)
        } else {
            accentColor = accent.rgb
        }

        if (primary == null) {
            primary = palette.lightVibrantSwatch

            if (primary == null) {
                primary = palette.darkVibrantSwatch

                if (primary == null) {
                    primary = palette.lightMutedSwatch

                    if (primary == null) {
                        primary = palette.mutedSwatch

                        if (primary == null) {
                            primary = palette.darkMutedSwatch
                        }
                    }
                }
            }
        }

        if (primary == null) {
            primaryColor = ContextCompat.getColor(this, R.color.primary)
        } else {
            primaryColor = primary.rgb
        }

        this.setThemeColors(primaryColor, accentColor)
    }

    override fun success(genericResponse: GenericResponse?, response: Response?) {
        if (genericResponse?.message?.isNotBlank() ?: false) {
            Toast.makeText(this, genericResponse?.message, Toast.LENGTH_SHORT).show()
        }
    }

    fun updateRemoteControlVisibility() {
        if (this.navigationView != null) {
            val hasRemotePlaybackClient = (this.application as ShowsRageApplication).hasPlayingVideo()

            this.navigationView!!.menu.findItem(R.id.menu_remote_control).isVisible = hasRemotePlaybackClient
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        this.setContentView(R.layout.activity_main)

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)

        if (savedInstanceState == null) {
            if (preferences.getBoolean("display_theme", true)) {
                this.delegate.setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                this.delegate.setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }

            this.recreate()
        }

        SickRageApi.instance.init(preferences)
        SickRageApi.instance.services?.getRootDirs(RootDirsCallback(this))

        this.appBarLayout = this.findViewById(R.id.app_bar) as AppBarLayout?
        this.drawerLayout = this.findViewById(R.id.drawer_layout) as DrawerLayout?
        this.navigationView = this.findViewById(R.id.drawer_content) as NavigationView?
        this.tabLayout = this.findViewById(R.id.tabs) as TabLayout?
        this.toolbar = this.findViewById(R.id.toolbar) as ColoredToolbar?

        if (this.drawerLayout != null) {
            this.drawerToggle = ActionBarDrawerToggle(this, this.drawerLayout, this.toolbar, R.string.abc_action_bar_home_description, R.string.abc_action_bar_home_description)

            (this.drawerLayout as DrawerLayout).addDrawerListener(this.drawerToggle as ActionBarDrawerToggle)
            (this.drawerLayout as DrawerLayout).post {
                drawerToggle?.syncState()
            }
        }

        if (this.navigationView != null) {
            this.drawerHeader = (this.navigationView as NavigationView).inflateHeaderView(R.layout.drawer_header) as LinearLayout?

            this.navigationView?.setNavigationItemSelectedListener(this)
        }

        if (this.toolbar != null) {
            this.setSupportActionBar(this.toolbar)
        }

        val intent = this.intent

        if (intent != null) {
            // Set the colors of the Activity
            val colorAccent = intent.getIntExtra(Constants.Bundle.COLOR_ACCENT, 0)
            val colorPrimary = intent.getIntExtra(Constants.Bundle.COLOR_PRIMARY, 0)

            if (colorPrimary != 0) {
                this.setThemeColors(colorPrimary, colorAccent)
            }
        }

        this.displayStartFragment()
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(this.receiver)

        super.onPause()
    }

    override fun onResume() {
        super.onResume()

        val intentFilter = IntentFilter()
        intentFilter.addAction(Constants.Intents.ACTION_EPISODE_ACTION_SELECTED)
        intentFilter.addAction(Constants.Intents.ACTION_EPISODE_SELECTED)
        intentFilter.addAction(Constants.Intents.ACTION_SEARCH_RESULT_SELECTED)
        intentFilter.addAction(Constants.Intents.ACTION_SHOW_SELECTED)

        LocalBroadcastManager.getInstance(this).registerReceiver(this.receiver, intentFilter)

        this.updateRemoteControlVisibility()
        this.checkForUpdate(false)
    }

    private fun checkForUpdate(manualCheck: Boolean) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val lastVersionCheckTime = preferences.getLong(Constants.Preferences.Fields.LAST_VERSION_CHECK_TIME, 0L)
        val checkInterval = preferences.getString("behavior_version_check", "0").toLong()

        if (shouldCheckForUpdate(checkInterval, manualCheck, lastVersionCheckTime)) {
            SickRageApi.instance.services?.checkForUpdate(CheckForUpdateCallback(this, manualCheck))
        }
    }

    private fun displayStartFragment() {
        val data = this.intent.data ?: return

        // Start the correct Setting Fragment, if necessary
        val settingFragment = getSettingFragmentForPath(data.path)

        if (settingFragment != null) {
            this.fragmentManager.beginTransaction()
                    .replace(R.id.content, settingFragment)
                    .commit()

            return
        }

        this.navigationView?.menu?.performIdentifierAction(R.id.menu_shows, 0)
    }

    private fun removeCurrentFragment() {
        val fragmentManager = this.fragmentManager
        val fragment = fragmentManager.findFragmentById(R.id.content)

        if (fragment != null) {
            fragmentManager.beginTransaction()
                    .remove(fragment)
                    .commit()
        }
    }

    private fun removeCurrentSupportFragment() {
        val fragmentManager = this.supportFragmentManager
        val fragment = fragmentManager.findFragmentById(R.id.content)

        if (fragment != null) {
            fragmentManager.beginTransaction()
                    .remove(fragment)
                    .commit()
        }
    }

    private fun resetThemeColors() {
        val colorAccent = ContextCompat.getColor(this, R.color.accent)
        val colorPrimary = ContextCompat.getColor(this, R.color.primary)

        this.setThemeColors(colorPrimary, colorAccent)
    }

    private fun setThemeColors(colorPrimary: Int, colorAccent: Int) {
        val textColor = Utils.getContrastColor(colorPrimary)

        this.appBarLayout?.setBackgroundColor(colorPrimary)

        if (this.drawerHeader != null) {
            (this.drawerHeader as LinearLayout).setBackgroundColor(colorPrimary)

            val logo = (this.drawerHeader as LinearLayout).findViewById(R.id.app_logo) as ImageView?
            val name = (this.drawerHeader as LinearLayout).findViewById(R.id.app_name) as TextView?

            if (logo != null) {
                val drawable = DrawableCompat.wrap(logo.drawable)
                DrawableCompat.setTint(drawable, textColor)
            }

            name?.setTextColor(textColor)
        }

        if (this.navigationView != null) {
            val colors = intArrayOf(colorPrimary, Color.WHITE)
            val states = arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()
            )
            val colorStateList = ColorStateList(states, colors)

            (this.navigationView as NavigationView).itemIconTintList = colorStateList
            (this.navigationView as NavigationView).itemTextColor = colorStateList
        }

        if (this.tabLayout != null) {
            val selectedTextColor = ColorUtils.setAlphaComponent(textColor, (0.7f * 255f).toInt())

            (this.tabLayout as TabLayout).setSelectedTabIndicatorColor(colorAccent)
            (this.tabLayout as TabLayout).setTabTextColors(selectedTextColor, textColor)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val colorPrimaryDark = floatArrayOf()
            ColorUtils.colorToHSL(colorPrimary, colorPrimaryDark)
            colorPrimaryDark[2] *= COLOR_DARK_FACTOR

            this.window.statusBarColor = ColorUtils.HSLToColor(colorPrimaryDark)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (textColor == Color.BLACK) {
                this.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }

        this.intent.putExtra(Constants.Bundle.COLOR_ACCENT, colorAccent)
        this.intent.putExtra(Constants.Bundle.COLOR_PRIMARY, colorPrimary)
    }

    companion object {
        fun getSettingFragmentForPath(path: String?): SettingsFragment? {
            return when (path) {
                "/" -> SettingsFragment()
                "/about" -> SettingsAboutFragment()
                "/about/licenses" -> SettingsAboutLicensesFragment()
                "/about/showsrage" -> SettingsAboutShowsRageFragment()
                "/behavior" -> SettingsBehaviorFragment()
                "/display" -> SettingsDisplayFragment()
                "/experimental_features" -> SettingsExperimentalFeaturesFragment()
                "/server" -> SettingsServerFragment()
                "/server/api_key" -> SettingsServerApiKeyFragment()
                else -> null
            }
        }

        fun shouldCheckForUpdate(checkInterval: Long, manualCheck: Boolean, lastCheckTime: Long): Boolean {
            // Always check for new version if the user triggered the version check himself
            if (manualCheck) {
                return true
            }

            // The automatic version check is disabled
            if (checkInterval == 0L) {
                return false
            }

            // Check if we need to look for new update, depending on the user preferences
            return System.currentTimeMillis() - lastCheckTime >= checkInterval
        }
    }

    private class CheckForUpdateCallback(activity: AppCompatActivity, val manualCheck: Boolean) : Callback<UpdateResponseWrapper> {
        private val activityReference: WeakReference<AppCompatActivity>;

        init {
            this.activityReference = WeakReference(activity)
        }

        override fun failure(error: RetrofitError?) {
            // SickRage may not support this request
            // SickRage version 4.0.30 is required
            if (this.manualCheck) {
                val activity = this.activityReference.get()

                if (activity != null) {
                    Toast.makeText(activity, R.string.sickrage_4030_required, Toast.LENGTH_SHORT).show()
                }
            }

            error?.printStackTrace()
        }

        override fun success(updateResponseWrapper: UpdateResponseWrapper?, response: Response?) {
            this.handleCheckForUpdateResponse(updateResponseWrapper?.data, this.manualCheck)
        }

        private fun handleCheckForUpdateResponse(update: UpdateResponse?, manualCheck: Boolean) {
            val activity = this.activityReference.get()

            if (activity == null || update == null) {
                return
            }

            with(PreferenceManager.getDefaultSharedPreferences(activity).edit()) {
                putLong(Constants.Preferences.Fields.LAST_VERSION_CHECK_TIME, System.currentTimeMillis())
                apply()
            }

            if (!update.needsUpdate()) {
                if (manualCheck) {
                    Toast.makeText(activity, R.string.no_update, Toast.LENGTH_SHORT).show()
                }

                return
            }

            if (manualCheck) {
                Toast.makeText(activity, R.string.new_update, Toast.LENGTH_SHORT).show()
            }

            val intent = Intent(activity, UpdateActivity::class.java)
            intent.putExtra(Constants.Bundle.UPDATE_MODEL, update)

            val pendingIntent = PendingIntent.getActivity(activity, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

            val notification = NotificationCompat.Builder(activity)
                    .setAutoCancel(true)
                    .setColor(ContextCompat.getColor(activity, R.color.primary))
                    .setContentIntent(pendingIntent)
                    .setContentTitle(activity.getString(R.string.app_name))
                    .setContentText(activity.getString(R.string.update_available))
                    .setLocalOnly(true)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(
                            activity.getString(R.string.update_available_detailed, update.currentVersion.version, update.latestVersion.version, update.commitsOffset)
                    ))
                    .build()

            val notificationManager = activity.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(0, notification)
        }
    }

    private class RootDirsCallback(activity: AppCompatActivity) : Callback<RootDirs> {
        private val activityReference: WeakReference<AppCompatActivity>;

        init {
            this.activityReference = WeakReference(activity)
        }

        override fun failure(error: RetrofitError?) {
            error?.printStackTrace()
        }

        override fun success(rootDirs: RootDirs?, response: Response?) {
            val activity = this.activityReference.get()

            if (activity == null || rootDirs == null) {
                return
            }

            val data = rootDirs.data
            val rootPaths = data.map { it.location }.toHashSet()

            with(PreferenceManager.getDefaultSharedPreferences(activity).edit()) {
                putStringSet(Constants.Preferences.Fields.ROOT_DIRS, rootPaths)
                apply()
            }
        }
    }
}