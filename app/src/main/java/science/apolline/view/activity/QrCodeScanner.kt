package science.apolline.view.activity

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.google.zxing.integration.android.IntentIntegrator
import es.dmoral.toasty.Toasty
import kotlinx.android.synthetic.main.activity_qr_code_scanner.*
import science.apolline.R

class QrCodeScanner : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_code_scanner)
        initFunc()

    }

    private fun initFunc() {
        //implement button action
        btn_scan_qr.setOnClickListener{
            initScan()
        }
    }

    private fun initScan(){
        IntentIntegrator(this).initiateScan()
    }

    // TODO Check validity qr code
    // TODO Add influx db url
    // TODO need to be called when user want to add a data export server
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode,resultCode,data)

        //check if result is scan any qr
        if(result != null){
            if(result.contents == null) {
                // the result is null or empty then
                Toasty.error(this, "The data is empty").show()
            }else{
                //this error because result maybe empty so use settext
                et_value_qr.setText(result.contents.toString())

            }
        }else{
            //the camera will not clise if the result is still null
            super.onActivityResult(requestCode, resultCode, data)
        }

    }
}
