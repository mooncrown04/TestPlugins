package com.anhdaden

import android.view.*
import android.widget.*
import android.os.Bundle
import android.net.Uri
import android.content.*
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import androidx.appcompat.app.AlertDialog
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.*

class XtreamIPTVSettingsFragment(private val plugin: XtreamIPTVPlugin) : BottomSheetDialogFragment() {
    private fun <T : View> View.findView(name: String): T {
        val id = plugin.resources!!.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        return this.findViewById(id)
    }

    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = plugin.resources!!.getIdentifier(name, "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        val layout = plugin.resources!!.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    private fun getDrawable(name: String): Drawable? {
        val id = plugin.resources!!.getIdentifier(name, "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        return ResourcesCompat.getDrawable(plugin.resources!!, id, null)
    }

    private fun getString(name: String): String? {
        val id = plugin.resources!!.getIdentifier(name, "string", BuildConfig.LIBRARY_PACKAGE_NAME)
        return plugin.resources!!.getString(id)
    }

    private fun View.makeTvCompatible() {
        val outlineId = plugin.resources!!.getIdentifier("outline", "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        this.background = plugin.resources!!.getDrawable(outlineId, null)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val settings = getLayout("settings", inflater, container)
        settings.findView<TextView>("add_link_text").text = "LINK EKLE"
        settings.findView<TextView>("list_link_text").text = "LINK LISTESİ"
        settings.findView<TextView>("group_text").text = "04: MoOnCrOwN"

        val addLinkButton = settings.findView<ImageView>("button_add_link")
        addLinkButton.setImageDrawable(getDrawable("edit_icon"))
        addLinkButton.makeTvCompatible()

        addLinkButton.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                val credsView = getLayout("add_link", inflater, container)
                val nameInput = credsView.findView<EditText>("name")
                val linkInput = credsView.findView<EditText>("link")
                val usernameInput = credsView.findView<EditText>("username")
                val passwordInput = credsView.findView<EditText>("password")

                val clipboardManager = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clipboardText = clipboardManager?.primaryClip?.getItemAt(0)?.text
                if (!clipboardText.isNullOrEmpty() && clipboardText.startsWith("http", ignoreCase = true)) {
                    linkInput.setText(clipboardText.toString())
                }

                AlertDialog.Builder(context ?: throw Exception("Unable to build alert dialog"))
                    .setTitle("Add link")
                    .setView(credsView)
                    .setPositiveButton("Save", object : DialogInterface.OnClickListener {
                        override fun onClick(p0: DialogInterface, p1: Int) {
                            var name = nameInput.text.trim().toString()
                            var link = linkInput.text.trim().toString().replace(Regex("^(HTTPS|HTTP)", RegexOption.IGNORE_CASE)) {
                                it.value.lowercase()
                            }
                            var username = usernameInput.text.trim().toString()
                            var password = passwordInput.text.trim().toString()
                            if (name.isNullOrEmpty() || !link.startsWith("http") || username.isNullOrEmpty() || password.isNullOrEmpty()) {
                                showToast("Please fill in all information")
                            } else {
                                val existingLinks = getKey<Array<Link>>("xtream_iptv_links") ?: emptyArray()
                                if (existingLinks.any { it.name == name }) {
                                    showToast("This name already exists")
                                } else {
                                    val updatedLinks = existingLinks.toMutableList().apply {
                                        add(Link(name, link, username, password))
                                    }
                                    setKey("xtream_iptv_links", updatedLinks)
                                    plugin.reload()
                                    showToast("Save link sucess")
                                }
                            }
                        }
                    })
                    .show()
            }
        })

        val listLinkButton = settings.findView<ImageView>("button_list_link")
        listLinkButton.setImageDrawable(getDrawable("edit_icon"))
        listLinkButton.makeTvCompatible()

        listLinkButton.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                val credsView = getLayout("manager", inflater, container)
                val linkListLayout = credsView.findView<LinearLayout>("list_link")
                val savedLinks = getKey<Array<Link>>("xtream_iptv_links") ?: emptyArray()
                if (savedLinks.isEmpty()) {
                    val noLinksTextView = TextView(context).apply {
                        text = "There are no links yet"
                        textSize = 16f
                        setTextColor(Color.GRAY)
                        gravity = Gravity.START
                    }
                    val layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        leftMargin = dpToPx(8)
                    }
                    noLinksTextView.layoutParams = layoutParams
                    linkListLayout.addView(noLinksTextView)
                } else {
                    savedLinks.forEach { link ->
                        val linkItemView = getLayout("list_link", inflater, container)
                        linkItemView.findView<TextView>("name").text = link.name 
                        linkItemView.findView<TextView>("link").text = link.mainUrl
                        val deleteButton = linkItemView.findView<ImageView>("delete_button")
                        deleteButton.setImageDrawable(getDrawable("delete_icon"))
                        deleteButton.setOnClickListener(object : View.OnClickListener {
                            override fun onClick(v: View?) {
                                val savedLinks = getKey<Array<Link>>("xtream_iptv_links") ?: emptyArray()
                                val updatedLinks = savedLinks.filter { it.name != link.name }.toTypedArray()
                                setKey("xtream_iptv_links", updatedLinks)
                                plugin.reload()
                                showToast("Remove success ${link.name}")
                                linkListLayout.removeView(linkItemView)
                            }
                        })
                        linkListLayout.addView(linkItemView)
                    }
                }

                AlertDialog.Builder(context ?: throw Exception("Unable to build alert dialog"))
                    .setTitle("List link Xtream IPTV")
                    .setView(credsView)
                    .show()
            }
        })

        val groupButton = settings.findView<ImageView>("button_group")
        groupButton.setImageDrawable(getDrawable("telegram"))
        groupButton.makeTvCompatible()

        groupButton.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                val url = "https://t.me/cloudstream_extension_vn"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            }
        })

        return settings
    }

    fun dpToPx(dp: Int): Int {
        val scale = context!!.resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }
}