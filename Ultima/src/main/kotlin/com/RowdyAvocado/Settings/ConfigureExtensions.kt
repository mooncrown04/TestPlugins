package com.RowdyAvocado

import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast
import androidx.fragment.app.Fragment // Fragment sınıfını import edin
import android.content.Context // Context sınıfını import edin
import androidx.core.content.ContextCompat // ContextCompat sınıfını import edin

// BuildConfig sınıfının doğru paketini import edin.
// build.gradle.kts dosyanızdaki 'namespace' değerine göre değişir.
// Ultima için "com.RowdyAvocado" olarak ayarlandığı varsayılmıştır.
import com.RowdyAvocado.BuildConfig

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

// UltimaPlugin parametresi kaldırıldı, Fragment'ın kendi context ve resources'ı kullanılacak.
class UltimaConfigureExtensions() : BottomSheetDialogFragment() {
    private var param1: String? = null
    private var param2: String? = null

    // Fragment'ın kaynaklarına erişmek için requireContext().resources kullanın.
    // Bu, Fragment'ın bir Context'e bağlı olduğundan emin olunduktan sonra güvenlidir.
    private val sm = UltimaStorageManager
    private val extensions = sm.fetchExtensions()
    private lateinit var res: Resources // lateinit olarak tanımlandı, onCreate'de veya onCreateView'de başlatılacak

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // res değişkenini burada başlatmak daha güvenlidir, çünkü context artık mevcuttur.
        res = requireContext().resources 
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    // #region - necessary functions
    // Bu fonksiyon, bir layout ID'sini kullanarak bir View'ı inflate eder.
    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = res.getIdentifier(name, "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        // Resources.getLayout() yerine LayoutInflater.inflate() kullanıldı
        return inflater.inflate(id, container, false)
    }

    private fun getDrawable(name: String): Drawable {
        val id = res.getIdentifier(name, "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        // ContextCompat.getDrawable kullanıldı
        return ContextCompat.getDrawable(requireContext(), id) ?: throw Exception("Unable to find drawable $name")
    }

    private fun getString(name: String): String {
        val id = res.getIdentifier(name, "string", BuildConfig.LIBRARY_PACKAGE_NAME)
        return res.getString(id)
    }

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        return this.findViewById(id)
    }

    private fun View.makeTvCompatible() {
        val outlineId = res.getIdentifier("outline", "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        // ContextCompat.getDrawable kullanıldı
        this.background = ContextCompat.getDrawable(requireContext(), outlineId)
            ?: throw Exception("Unable to find drawable outline")
    }
    // #endregion - necessary functions

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val settings = getLayout("configure_extensions", inflater, container)

        // #region - building save button and its click listener
        val saveBtn = settings.findView<ImageView>("save")
        saveBtn.setImageDrawable(getDrawable("save_icon"))
        saveBtn.makeTvCompatible()
        saveBtn.setOnClickListener(
            object : OnClickListener {
                override fun onClick(btn: View) {
                    // plugin.reload(context) yerine Cloudstream'in reload mekanizması kullanılmalı
                    // veya bu işlevselliği doğrudan burada uygulayın.
                    // Eğer UltimaPlugin'in reload metodu Context gerektiriyorsa:
                    // plugin.reload(requireContext())
                    sm.currentExtensions = extensions
                    showToast(requireContext(), "Saved") // showToast için context parametresi eklendi
                    dismiss()
                }
            }
        )
        // #endregion - building save button and its click listener

        // #region - building toggle for extension_name_on_home and its click listener
        val extNameOnHomeBtn = settings.findView<Switch>("ext_name_on_home_toggle")
        extNameOnHomeBtn.makeTvCompatible()
        extNameOnHomeBtn.isChecked = sm.extNameOnHome
        extNameOnHomeBtn.setOnClickListener(
            object : OnClickListener {
                override fun onClick(btn: View) {
                    sm.extNameOnHome = extNameOnHomeBtn.isChecked
                }
            }
        )
        // #endregion - building toggle for extension_name_on_home and its click listener

        // #region - building list of extensions and its sections with its click listener
        val extensionsListLayout = settings.findView<LinearLayout>("extensions_list")
        extensions.forEach { extension ->
            val extensionLayoutView = buildExtensionView(extension, inflater, container)
            extensionsListLayout.addView(extensionLayoutView)
        }
        // #endregion - building list of extensions and its sections with its click listener

        return settings
    }

    // #region - functions which lists extensions and its sections with counters
    fun buildExtensionView(
        extension: UltimaUtils.ExtensionInfo,
        inflater: LayoutInflater,
        container: ViewGroup?
    ): View {

        fun buildSectionView(
            section: UltimaUtils.SectionInfo,
            inflater: LayoutInflater,
            container: ViewGroup?
        ): View {

            // collecting required resources
            val sectionView = getLayout("list_section_item", inflater, container)
            val childCheckBoxBtn = sectionView.findView<CheckBox>("section_checkbox")

            // building section checkbox and its click listener
            childCheckBoxBtn.text = section.name
            childCheckBoxBtn.makeTvCompatible()
            childCheckBoxBtn.isChecked = section.enabled
            childCheckBoxBtn.setOnCheckedChangeListener(
                object : OnCheckedChangeListener {
                    override fun onCheckedChanged(
                        buttonView: CompoundButton,
                        isChecked: Boolean
                    ) {
                        section.enabled = isChecked
                    }
                }
            )

            return sectionView
        }

        // collecting required resources
        val extensionLayoutView = getLayout("list_extension_item", inflater, container)
        val extensionDataBtn = extensionLayoutView.findView<LinearLayout>("extension_data")
        val expandImage = extensionLayoutView.findView<ImageView>("expand_icon")
        expandImage.setImageDrawable(getDrawable("triangle"))
        val extensionNameBtn = extensionDataBtn.findView<TextView>("extension_name")
        val childList = extensionLayoutView.findView<LinearLayout>("sections_list")

        // building extension textview and its click listener
        expandImage.setRotation(90f)
        extensionNameBtn.text = extension.name
        extensionDataBtn.makeTvCompatible()

        extensionDataBtn.setOnClickListener(
            object : OnClickListener {
                override fun onClick(btn: View) {
                    if (childList.visibility == View.VISIBLE) {
                        childList.visibility = View.GONE
                        expandImage.setRotation(90f)
                    } else {
                        childList.visibility = View.VISIBLE
                        expandImage.setRotation(180f)
                    }
                }
            }
        )

        // building list of sections of current extnesion with its click listener
        extension.sections?.forEach { section ->
            val newSectionView = buildSectionView(section, inflater, container)
            childList.addView(newSectionView)
        }
        return extensionLayoutView
    }
    // #endregion - functions which lists extensions and its sections with counters

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState) // super.onViewCreated çağrısı eklendi
    }

    override fun onDetach() {
        super.onDetach() // super.onDetach çağrısı eklendi
        // UltimaSettings'i göstermek için requireActivity().supportFragmentManager kullanıldı
        val settings = UltimaSettings() // UltimaSettings'in constructor'ı güncellendiyse parametre kaldırıldı
        settings.show(
            requireActivity().supportFragmentManager, // requireActivity() kullanıldı
            ""
        )
    }
}
