package com.RowdyAvocado

import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast
import android.content.Context // Context sınıfı import edildi
import androidx.core.content.ContextCompat // ContextCompat sınıfı import edildi
import androidx.fragment.app.Fragment // Fragment sınıfı import edildi

// BuildConfig sınıfının doğru paketini import edin.
// build.gradle.kts dosyanızdaki 'namespace' değerine göre değişir.
// Ultima için "com.RowdyAvocado" olarak ayarlandığı varsayılmıştır.
import com.RowdyAvocado.BuildConfig

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

// UltimaPlugin parametresi kaldırıldı, Fragment'ın kendi context ve resources'ı kullanılacak.
class UltimaReorder() : BottomSheetDialogFragment() {
    private var param1: String? = null
    private var param2: String? = null
    private val sm = UltimaStorageManager
    private val extensions = sm.fetchExtensions()
    private lateinit var res: Resources // lateinit olarak tanımlandı, onCreate'de başlatılacak

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
        // super.onCreateView çağrısı burada gerekli değil, doğrudan View döndürülüyor
        val settings = getLayout("reorder", inflater, container)

        // #region - building save button and its click listener
        val saveBtn = settings.findView<ImageView>("save")
        saveBtn.setImageDrawable(getDrawable("save_icon"))
        saveBtn.makeTvCompatible()
        saveBtn.setOnClickListener(
            object : OnClickListener {
                override fun onClick(btn: View) {
                    sm.currentExtensions = extensions
                    // plugin.reload(context) yerine Cloudstream'in reload mekanizması kullanılmalı
                    // veya bu işlevselliği doğrudan burada uygulayın.
                    // Eğer UltimaPlugin'in reload metodu Context gerektiriyorsa:
                    // plugin.reload(requireContext())
                    showToast(requireContext(), "Saved") // showToast için context parametresi eklendi
                    dismiss()
                }
            }
        )
        // #endregion - building save button and its click listener

        // #region - building list view for sections
        val noSectionWarning = settings.findView<TextView>("no_section_warning")
        val sectionsListView = settings.findView<LinearLayout>("section_list")
        updateSectionList(sectionsListView, inflater, container, noSectionWarning)
        // #region - building list view for sections

        return settings
    }

    private fun updateSectionList(
        sectionsListView: LinearLayout,
        inflater: LayoutInflater,
        container: ViewGroup?,
        noSectionWarning: TextView? = null,
        focusingSection: Int? = null,
        focusOn: String? = null,
    ) {
        sectionsListView.removeAllViews()

        var sections = emptyList<UltimaUtils.SectionInfo>()
        extensions.forEach { it.sections?.forEach { if (it.enabled) sections += it } }

        var counter = sections.size
        if (counter <= 0) noSectionWarning?.visibility = View.VISIBLE
        sections.sortedByDescending { it.priority }.forEach {
            val sectionView = getLayout("list_section_reorder_item", inflater, container)
            val sectionName = sectionView.findView<TextView>("section_name")
            val increaseBtn = sectionView.findView<ImageView>("increase")
            val decreaseBtn = sectionView.findView<ImageView>("decrease")
            increaseBtn.setImageDrawable(getDrawable("triangle"))
            decreaseBtn.setImageDrawable(getDrawable("triangle"))
            decreaseBtn.setRotation(180f)

            if (it.priority == 0) it.priority = counter // .equals(0) yerine == kullanıldı
            sectionName.text = "${it.pluginName}: ${it.name}"

            // configuring click listener for increase button
            increaseBtn.makeTvCompatible()
            increaseBtn.setOnClickListener(
                object : OnClickListener {
                    override fun onClick(btn: View) {
                        if (it.priority < sections.size) {
                            val oldSection =
                                sections.find { s -> s.priority == (it.priority + 1) } // .equals() yerine == kullanıldı
                                    ?: throw Exception()
                            oldSection.priority -= 1
                            it.priority += 1
                            updateSectionList(
                                sectionsListView,
                                inflater,
                                container,
                                null,
                                it.priority,
                                "increase"
                            )
                        }
                    }
                }
            )

            // configuring click listener for decrease button
            decreaseBtn.makeTvCompatible()
            decreaseBtn.setOnClickListener(
                object : OnClickListener {
                    override fun onClick(btn: View) {
                        if (it.priority > 1) {
                            val oldSection =
                                sections.find { s -> s.priority == (it.priority - 1) } // .equals() yerine == kullanıldı
                                    ?: throw Exception()
                            oldSection.priority += 1
                            it.priority -= 1
                            updateSectionList(
                                sectionsListView,
                                inflater,
                                container,
                                null,
                                it.priority,
                                "decrease"
                            )
                        }
                    }
                }
            )
            if (counter == focusingSection) // .equals() yerine == kullanıldı
                when (focusOn) {
                    "increase" -> increaseBtn.requestFocus()
                    "decrease" -> decreaseBtn.requestFocus()
                    else -> {}
                }
            counter -= 1
            sectionsListView.addView(sectionView)
        }
    }

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
