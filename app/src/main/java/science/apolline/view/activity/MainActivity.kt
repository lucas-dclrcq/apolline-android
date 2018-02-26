package science.apolline.view.activity


import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.wifi.WifiManager.WifiLock
import android.os.Bundle
import android.os.PowerManager.WakeLock
import android.support.design.widget.NavigationView
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.app.FragmentTransaction
import android.support.v4.view.GravityCompat
import android.support.v4.view.ViewPager
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.birbit.android.jobqueue.JobManager
import com.github.salomonbrys.kodein.instance
import com.github.salomonbrys.kodein.with
import es.dmoral.toasty.Toasty
import kotlinx.android.synthetic.main.activity_main_app_bar.*
import kotlinx.android.synthetic.main.activity_main_content.*
import org.jetbrains.anko.*
import pub.devrel.easypermissions.EasyPermissions
import science.apolline.R
import science.apolline.service.sensor.IOIOService
import science.apolline.service.synchronisation.SyncInfluxDBJob
import science.apolline.utils.CheckUtility.isNetworkConnected
import science.apolline.utils.CheckUtility.requestDozeMode
import science.apolline.utils.SyncJobScheduler.cancelAutoSync
import science.apolline.utils.SyncJobScheduler.setAutoSync
import science.apolline.view.fragment.IOIOFragment
import science.apolline.root.RootActivity
import science.apolline.utils.CheckUtility.requestLocation
import science.apolline.view.fragment.MapFragment


class MainActivity : RootActivity(), NavigationView.OnNavigationItemSelectedListener, EasyPermissions.PermissionCallbacks, AnkoLogger {

    private val jobManager by instance<JobManager>()

    private val fragmentIOIO  by instance<IOIOFragment>()

    private val fragmentMaps  by instance<MapFragment>()

    private  val wakeLock: WakeLock by with(this as AppCompatActivity).instance()

    private val wifiLock: WifiLock by with(this as AppCompatActivity).instance()

    private lateinit var requestLocationAlert: AlertDialog

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        val toggle = ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer.addDrawerListener(toggle)
        toggle.syncState()

        val navigationView = findViewById<NavigationView>(R.id.nav_drawer)
        navigationView.setNavigationItemSelectedListener(this)

        // Check permissions
        checkPermissions()

        // Request enable Bluetooth
        requestBluetooth()

        // Request disable Doze Mode
        requestDozeMode(this)

        // Request enable location
        requestLocationAlert = requestLocation(this)

        // Launch AutoSync
        setAutoSync(INFLUXDB_SYNC_FREQ, this)

        // VIewPager
        setupViewPager(pager)
        tabs.setupWithViewPager(pager)

        //fragmentIOIO = IOIOFragment()
        //replaceFragment(fragmentIOIO)

    }


    override fun onBackPressed() {
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
        } else {
            val backStackEntryCount = supportFragmentManager.backStackEntryCount
            if (backStackEntryCount == 1) {
                finish()
            } else {
                if (backStackEntryCount > 1) {
                    supportFragmentManager.popBackStack()
                } else {
                    super.onBackPressed()
                }
                super.onBackPressed()
            }

        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {


        if (isNetworkConnected(this)) {
            jobManager.addJobInBackground(SyncInfluxDBJob())
            Toasty.info(applicationContext, "Synchronization in progress", Toast.LENGTH_SHORT, true).show()
        } else {
            jobManager.addJobInBackground(SyncInfluxDBJob())
            Toasty.warning(applicationContext, "No internet connection ! Synchronization job added to queue", Toast.LENGTH_LONG, true).show()
        }
        return true
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        val itemId = item.itemId
        val groupId = item.groupId

        when (groupId) {
            R.id.grp_capteur -> if (itemId == R.id.nav_ioio) {
                val ioioFragment = IOIOFragment()
                replaceFragment(ioioFragment)
            }
            R.id.grp_fonction -> if (itemId == R.id.nav_setting) {
                val intent = Intent(this, OptionsActivity::class.java)
                startActivity(intent)
            } else if (itemId == R.id.nav_info) {
                val intent = Intent(this, InformationActivity::class.java)
                startActivity(intent)
                return true
            } else if (itemId == R.id.nav_contact) {
                val intent = Intent(this, ContactActivity::class.java)
                startActivity(intent)
            }
            else -> {
            }
        }//              if (itemId == R.id.nav_share) {
        //
        //              } else if (itemId == R.id.nav_send) {
        //
        //              }

        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        drawer.closeDrawer(GravityCompat.START)
        return true
    }


    private fun replaceFragment(fragment: Fragment) {
        val backStateName = fragment.javaClass.name

        val manager = supportFragmentManager
        val fragmentPopped = manager.popBackStackImmediate(backStateName, 0)

        if (!fragmentPopped && manager.findFragmentByTag(backStateName) == null) { //fragment not in back stack, create it.
            val ft = manager.beginTransaction()
            ft.replace(R.id.fragment, fragment, backStateName)
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            ft.addToBackStack(backStateName)
            ft.commit()
        }
    }

    private fun checkPermissions(): Boolean {
        if (!EasyPermissions.hasPermissions(this, *PERMISSIONS_ARRAY)) {
            EasyPermissions.requestPermissions(this, "Geolocation and writing permissions are necessary for the proper functioning of the application", REQUEST_CODE_PERMISSIONS_ARRAY,
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }


    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>?) {
        finish()
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>?) {
    }


    private fun requestBluetooth() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (!bluetoothAdapter.isEnabled) {
            val enableBlueTooth = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBlueTooth, REQUEST_CODE_ENABLE_BLUETOOTH)
        }
    }

    override fun onPause() {
        super.onPause()
        //MoveViewJob.getInstance(null, 0.0F, 0.0F, null, null)
    }

    override fun onStop() {
        super.onStop()
        //MoveViewJob.getInstance(null, 0f, 0f, null, null)
    }



    private fun setupViewPager(pager: ViewPager?) {
        val adapter = Adapter(supportFragmentManager)

        val f1 = fragmentIOIO
        adapter.addFragment(f1, "IOIO")

        val f2 = fragmentMaps
        adapter.addFragment(f2, "MAP")

        pager?.adapter = adapter
    }

    private class Adapter(manager: FragmentManager) : FragmentPagerAdapter(manager) {
        val fragments = ArrayList<Fragment>()
        val titles = ArrayList<String>()
        override fun getItem(position: Int): Fragment = fragments.get(position)

        override fun getCount(): Int = fragments.size

        override fun getPageTitle(position: Int): CharSequence? = titles.get(position)

        fun addFragment(fragment: Fragment, title: String) {
            fragments.add(fragment)
            titles.add(title)
        }
    }




















    @SuppressLint("MissingSuperCall")
    override fun onDestroy() {
        if (wakeLock.isHeld) {
            wakeLock.release()
            info("wakeLock released")
        } else if (wifiLock.isHeld) {
            wifiLock.release()
            info("wifiLock released")
        }
        super.onDestroy()
        cancelAutoSync(false)
        stopService(Intent(this, IOIOService::class.java))
        if ( requestLocationAlert.isShowing ){
            requestLocationAlert.cancel()
        }
    }


    companion object {

        const val MY_PREFS_NAME = "MyPrefsFile"
        private val PERMISSIONS_ARRAY = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        private const val REQUEST_CODE_PERMISSIONS_ARRAY = 100
        private const val REQUEST_CODE_ENABLE_BLUETOOTH = 101
        private const val REQUEST_CODE_FINE_LOCATION = 102
        private const val REQUEST_CODE_COARSE_LOCATION = 103
        private const val REQUEST_CODE_WRITE_EXTERNAL_STORAGE = 104
        private const val REQUEST_WAKE_UP_TIMEOUT: Long = 86400 // 24h

        private const val INFLUXDB_SYNC_JOB_ID = "influxDBJobId"

        private const val INFLUXDB_SYNC_FREQ: Long = 60 // Minutes

    }
}