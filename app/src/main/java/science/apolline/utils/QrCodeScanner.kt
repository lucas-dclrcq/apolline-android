package science.apolline.utils

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import science.apolline.R

class QrCodeScanner : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_code_scanner)
    }
}
