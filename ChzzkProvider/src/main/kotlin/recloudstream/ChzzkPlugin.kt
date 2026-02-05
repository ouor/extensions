package recloudstream

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.DataStore.getKey

@CloudstreamPlugin
class ChzzkPlugin: BasePlugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(ChzzkProvider())
    }

    override fun openSettings(context: Context) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Chzzk Settings")

        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 20, 50, 20)
        
        val instructions = TextView(context)
        instructions.text = "Enter your NID_AUT and NID_SES cookies to access 1080p and age-restricted content."
        instructions.setPadding(0, 0, 0, 20)
        layout.addView(instructions)

        val nidAutInput = EditText(context)
        nidAutInput.hint = "NID_AUT"
        nidAutInput.setText(context.getKey<String>("CHZZK_NID_AUT") ?: "")
        layout.addView(nidAutInput)

        val nidSesInput = EditText(context)
        nidSesInput.hint = "NID_SES"
        nidSesInput.setText(context.getKey<String>("CHZZK_NID_SES") ?: "")
        layout.addView(nidSesInput)

        builder.setView(layout)

        builder.setPositiveButton("Save") { _, _ ->
            val aut = nidAutInput.text.toString().trim()
            val ses = nidSesInput.text.toString().trim()
            
            context.setKey("CHZZK_NID_AUT", aut)
            context.setKey("CHZZK_NID_SES", ses)
            
            showToast(context, "Settings Saved")
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }
    
    private fun showToast(context: Context, message: String) {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
