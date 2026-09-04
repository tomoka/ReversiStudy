package mobi.tomo.reversi

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 画面の向きは AndroidManifest.xml で縦固定にしている
        setContentView(ReversiView(this))
    }
}
