package mobi.tomo.reversi

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val view = ReversiView(this)
        view.keepScreenOn = true
        setContentView(view)

        // targetSdk 35 以降は画面全体に描くため、システムバーの下に潜り込まないよう
        // インセットを padding として渡す。View 側はこの padding を見て盤を配置する。
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            target.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            windowInsets
        }
    }
}
