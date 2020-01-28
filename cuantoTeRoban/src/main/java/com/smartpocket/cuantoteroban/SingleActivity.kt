package com.smartpocket.cuantoteroban

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import com.android.billingclient.api.BillingClient
import kotlinx.android.synthetic.main.activity_single.*

class SingleActivity : AppCompatActivity() {

    private lateinit var adViewHelper: AdViewHelper
    private lateinit var singleActivityVM: SingleActivityVM
    private lateinit var billingHelper: BillingHelper
//    private lateinit var appBarConfiguration: AppBarConfiguration


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_single)
//        val toolbar = toolbar as Toolbar
//        setSupportActionBar(toolbar)
//        val navController = findNavController(R.id.nav_host_fragment)
//        appBarConfiguration = AppBarConfiguration(navController.graph)
//        setupActionBarWithNavController(navController, appBarConfiguration)
//        toolbar.setupWithNavController(navController, appBarConfiguration)
//        setupActionBarWithNavController(this, navController)
        adViewHelper = AdViewHelper(adViewContainer, this)
        billingHelper = BillingHelper(this)

        singleActivityVM = ViewModelProvider(this)[SingleActivityVM::class.java]
        singleActivityVM.billingStatusLD.observe(this, Observer {
            onBillingHelperStatusChanged(it)
        })
        singleActivityVM.showAdsLD.observe(this, Observer {
            adViewHelper.showBanner(it)
        })
        singleActivityVM.launchPurchaseLD.observe(this, Observer {
            billingHelper.launchBillingFlow(this)
        })
        singleActivityVM.launchRestoreAdsLD.observe(this, Observer {
            billingHelper.consumeRemoveAdsPurchase()
        })
    }

    override fun onDestroy() {
        adViewHelper.destroy()
        super.onDestroy()
    }

    override fun onSupportNavigateUp() =
            findNavController(R.id.nav_host_fragment).navigateUp()

    fun onBillingHelperStatusChanged(code: Int) {
        @StringRes val msg: Int
        if (code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            msg = R.string.billing_status_already_owned
            adViewHelper.destroy()
        } else if (code == BillingClient.BillingResponseCode.OK) {
            msg = R.string.billing_status_thanks_for_buying
            adViewHelper.destroy()
        } else if (code == BillingClient.BillingResponseCode.USER_CANCELED) {
            msg = R.string.billing_status_canceled
        } else if (code == BillingClient.BillingResponseCode.ERROR) {
            msg = R.string.billing_status_error
        } else if (code == PURCHASE_STATE_PENDING) {
            msg = R.string.billing_status_pending
        } else {
            msg = R.string.billing_status_connection_error
        }
        Utilities.showToast(getString(msg))
    }
}
