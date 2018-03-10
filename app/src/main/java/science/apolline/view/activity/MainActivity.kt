package science.apolline.view.activity


import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent.getActivity
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
import science.apolline.utils.SyncJobScheduler.cancelAutoSync
import science.apolline.view.fragment.IOIOFragment
import science.apolline.root.RootActivity
import science.apolline.utils.CheckUtility
import science.apolline.utils.SyncJobScheduler
import science.apolline.view.fragment.ViewPagerFragment
import java.io.IOException


class MainActivity : RootActivity(), NavigationView.OnNavigationItemSelectedListener, EasyPermissions.PermissionCallbacks, AnkoLogger {

    private val mJobManager by instance<JobManager>()
    private val mFragmentViewPager by instance<ViewPagerFragment>()
    private val mWakeLock: WakeLock by with(this as AppCompatActivity).instance()
    private val mWifiLock: WifiLock by with(this as AppCompatActivity).instance()
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private lateinit var mRequestLocationAlert: AlertDialog

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

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        // Request enable Bluetooth
        checkBlueToothState()
        // Check permissions
        checkPermissions()
        // Request disable Doze Mode
        CheckUtility.requestDozeMode(this)
        // Request enable location
        mRequestLocationAlert = CheckUtility.requestLocation(this)

        // Launch AutoSync
        SyncJobScheduler.setAutoSync(INFLUXDB_SYNC_FREQ, this)

        replaceFragment(mFragmentViewPager)
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

        if (CheckUtility.isNetworkConnected(this)) {
            mJobManager.addJobInBackground(SyncInfluxDBJob())
            Toasty.info(applicationContext, "Synchronization in progress", Toast.LENGTH_SHORT, true).show()
        } else {
            mJobManager.addJobInBackground(SyncInfluxDBJob())
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
                val viewPagerFragment = ViewPagerFragment()
                replaceFragment(viewPagerFragment)
            }
            else -> {
            }
        }

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
            EasyPermissions.requestPermissions(this, "Geolocation, read phone state and writing permissions are necessary for the proper functioning of the application", REQUEST_CODE_PERMISSIONS_ARRAY,
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_PHONE_STATE)
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


    @SuppressLint("MissingSuperCall")
    override fun onDestroy() {
        if (mWakeLock.isHeld) {
            mWakeLock.release()
            info("WakeLock released")
        } else if (mWifiLock.isHeld) {
            mWifiLock.release()
            info("WifiLock released")
        }
        super.onDestroy()
        cancelAutoSync(false)
        stopService(Intent(this, IOIOService::class.java))
        if (mRequestLocationAlert.isShowing) {
            mRequestLocationAlert.cancel()
        }
    }

    private fun checkBlueToothState() {

        if (mBluetoothAdapter == null) {
            Toasty.error(applicationContext, "Bluetooth NOT support", Toast.LENGTH_SHORT, true).show()
        } else {
            if (mBluetoothAdapter!!.isEnabled) {
                if (mBluetoothAdapter!!.isDiscovering) {
                    Toasty.info(applicationContext, "Bluetooth is currently in device discovery process.", Toast.LENGTH_SHORT, true).show()
                } else {
                    info("Bluetooth is Enabled.")
                }
            } else {
                Toasty.warning(applicationContext, "Bluetooth NOT enabled.", Toast.LENGTH_SHORT, true).show()
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_CODE_ENABLE_BLUETOOTH)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_ENABLE_BLUETOOTH) {
            if (resultCode == Activity.RESULT_OK) {
                if (!IOIOService.getServiceStatus()) {
                    stopService(Intent(applicationContext, IOIOService::class.java))
                    startService(Intent(applicationContext, IOIOService::class.java))
                }
                Toasty.success(applicationContext, "Bluetooth is Enabled.", Toast.LENGTH_SHORT, true).show()
            } else {
                //checkBlueToothState()
                Toasty.error(applicationContext, "Bluetooth NOT enabled", Toast.LENGTH_LONG, true).show()
            }

        }
    }

    companion object {

        private val PERMISSIONS_ARRAY = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_PHONE_STATE)
        private const val REQUEST_CODE_PERMISSIONS_ARRAY = 100
        private const val REQUEST_CODE_ENABLE_BLUETOOTH = 101
        private const val INFLUXDB_SYNC_FREQ: Long = 60 // Minutes

    }
}
