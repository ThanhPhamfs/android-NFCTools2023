package com.example.nfctools2023

import android.app.Activity
import android.app.Dialog
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Byte
import kotlin.experimental.and

class MainActivity : AppCompatActivity() {
    /** NFC ADAPTER */
    private val NFC_TURN_OFF = 1
    private val NFC_NOT_AVAILABLE = 0
    private val NFC_AVAILABLE = 2

    private val MIME_TEXT_PLAIN = "text/plain"
    private val TAG = "NfcDemo"
    private var mNfcAdapter: NfcAdapter? = null
    private var tvContent: TextView? = null
    private var tvLanguageCode: TextView? = null
    private var customProgressDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvContent = findViewById(R.id.tvContent)
        tvLanguageCode = findViewById(R.id.tvLanguageCode)

        this.mNfcAdapter = NfcAdapter.getDefaultAdapter(this)
        /** Check mNfcAdapter **/
        if (mNfcAdapter == null) {
            // Stop here, we definitely need NFC
            Toast.makeText(this, "This device doesn't support NFC.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (!mNfcAdapter!!.isEnabled) {
            tvContent?.text = "NFC is disabled"
        } else {
            tvContent?.text = "NFC is available"
        }
//        Toast.makeText(this, "${checkNfcAdapter()}", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        mNfcAdapter?.let {
            setupForegroundDispatch(this, it)
        }
    }

    override fun onPause() {
        mNfcAdapter?.let { stopForegroundDispatch(this, it) };
        super.onPause()
    }

    override fun onNewIntent(intent: Intent?) {
        Log.d(TAG, "onNewIntent")
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    /**
     * Handle Intent object
     */
    private fun handleIntent(intent: Intent) {
        showProgressDialog()
        val action: String? = intent.action
        action?.let {
            when (it) {
                NfcAdapter.ACTION_NDEF_DISCOVERED -> {
                    Log.d(TAG, "ACTION_NDEF_DISCOVERED");
                    /** Using EXTRA_NDEF_MESSAGES */
//                    intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
//                        ?.also { rawMessages ->
//                            val messages: List<NdefMessage> = rawMessages.map { it as NdefMessage }
//                            Log.d(TAG, "size: " + messages[0].records[0].payload.size);
//                        }
                    val type = intent.type;
                    if (type != null){
                        if (MIME_TEXT_PLAIN == type) {
                            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
                            Log.d(TAG, "Correct mime type: " + type);
                            tag?.let {
                                lifecycleScope.launch {
                                    readTextFromTag(tag)
                                }
                            }

                        } else {
                            Log.d(TAG, "Wrong mime type: " + type);
                        }
                    }else{
                        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
                        tag?.let {
                            var ndef: Ndef = Ndef.get(tag)
                            var ndefMessage: NdefMessage = ndef.cachedNdefMessage
                            var records = ndefMessage.records
                            for (record in records){
                                Log.d(TAG, "${record.tnf} ${NdefRecord.TNF_WELL_KNOWN}");
                                Log.d(TAG, "${record.type} ${NdefRecord.TNF_UNKNOWN}")
                                if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type contentEquals NdefRecord.RTD_TEXT){
                                    runOnUiThread {
                                        val recordContent = String(record.payload)

                                        tvContent?.text = "Record content: ${recordContent.substring(3)}"
                                        tvLanguageCode?.text = "Language code: ${recordContent.substring(1, 3)}"
//                        tvExplanation?.text = "Tag type: " + String(record.type)
                                    }
                                }
                            }
                            cancelProgressDialog()
                        }
                    }
                }
                NfcAdapter.ACTION_TECH_DISCOVERED -> {
                    Log.d(TAG, "ACTION_TECH_DISCOVERED");
//                    val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
//                    var techList = tag?.techList
//                    var searchedTech = Ndef.get(tag)
//                    techList?.let { list ->
//                        for(tech in list){
//                            Log.d(TAG, "tech: " + tech);
//                        }
//                    }
                }
                else -> {
                    Log.d(TAG, "Another Action");
                }
            }
        }

    }

    /**
     * Foreground dispatch system
     */
    private fun setupForegroundDispatch(activity: Activity, adapter: NfcAdapter) {
        Log.d(TAG, "setupForegroundDispatch")
        val intent: Intent = Intent(activity.applicationContext, activity.javaClass)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pendingIntent: PendingIntent =
            PendingIntent.getActivity(activity.applicationContext, 0, intent, 0)
        val filter: IntentFilter = IntentFilter()
        filter.addAction(NfcAdapter.ACTION_NDEF_DISCOVERED)
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        try {
            filter.addDataType(MIME_TEXT_PLAIN)
        } catch (er: IntentFilter.MalformedMimeTypeException) {
            throw RuntimeException("Check your mime type.");
        }

//        Filter for uri
        val filterUri: IntentFilter = IntentFilter()
        filterUri.addAction(NfcAdapter.ACTION_NDEF_DISCOVERED)
        filterUri.addCategory(Intent.CATEGORY_DEFAULT)
        try {
            filterUri.addDataScheme("https")
        } catch (er: IntentFilter.MalformedMimeTypeException) {
            throw RuntimeException("Check your data scheme.");
        }
        var filters = arrayOf<IntentFilter>(filter, filterUri)
        var techList = arrayOf(arrayOf<String>())

//
        adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList)
    }

    /**
     * stop foreground dispatch system
     */
    private fun stopForegroundDispatch(activity: Activity, nfcAdapter: NfcAdapter) {
        nfcAdapter.disableForegroundDispatch(activity)
    }

    /**
     * Read text from tag
     */
    private suspend fun readTextFromTag(tag: Tag) {
        withContext(Dispatchers.IO) {
            var ndef: Ndef = Ndef.get(tag)
            var ndefMessage: NdefMessage = ndef.cachedNdefMessage
            var records = ndefMessage.records
            for (record in records){
                if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type contentEquals NdefRecord.RTD_TEXT){
                    runOnUiThread {
                        val recordContent = String(record.payload)

                        tvContent?.text = "Record content: ${recordContent.substring(3)}"
                        tvLanguageCode?.text = "Language code: ${recordContent.substring(1, 3)}"
//                        tvExplanation?.text = "Tag type: " + String(record.type)
                    }
                }
            }
            cancelProgressDialog()
        }
    }

    /**
     * Check Nfc Adapter
     */
    private fun checkNfcAdapter():Int{
        if (mNfcAdapter == null){
            return NFC_NOT_AVAILABLE
        }
        if (mNfcAdapter!!.isEnabled){
            return NFC_AVAILABLE
        }else{
            return NFC_TURN_OFF
        }
    }

    /**
     * Method is used to show the Custom Progress Dialog.
     */
    private fun showProgressDialog() {
        customProgressDialog = Dialog(this@MainActivity)

        /*Set the screen content from a layout resource.
        The resource will be inflated, adding all top-level views to the screen.*/
        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)

        //Start the dialog and display it on screen.
        customProgressDialog?.show()
    }


    /**
     * This function is used to dismiss the progress dialog if it is visible to user.
     */
    private fun cancelProgressDialog() {
        if (customProgressDialog != null) {
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }
}

