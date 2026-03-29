package com.close.hook.ads.ui.fragment.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.close.hook.ads.BuildConfig
import com.close.hook.ads.R
import com.close.hook.ads.databinding.FragmentHomeBinding
import com.close.hook.ads.debug.PerformanceActivity
import com.close.hook.ads.manager.ActivationStatus
import com.close.hook.ads.manager.ServiceManager
import com.close.hook.ads.ui.activity.AboutActivity
import com.close.hook.ads.ui.fragment.base.BaseFragment
import com.close.hook.ads.util.resolveColorAttr
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class HomeFragment : BaseFragment<FragmentHomeBinding>() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolBar()
        setSystemInfo()
        setHyperLinks()
        observeActivationStatus()
    }

    private fun observeActivationStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                ServiceManager.connectionState
                    .map { ServiceManager.activationStatus }
                    .distinctUntilChanged()
                    .collectLatest { status ->
                        updateStatusUI(status)
                    }
            }
        }
    }

    private fun updateStatusUI(status: ActivationStatus) {
        val context = requireContext()

        val (colorAttr, iconRes, titleText, summaryExtra) = when (status) {
            ActivationStatus.ACTIVE -> {
                val color = context.resolveColorAttr(android.R.attr.colorPrimary)
                Quadruple(color, R.drawable.ic_round_check_circle_24,
                    getString(R.string.activated), null)
            }
            ActivationStatus.HOOKS_ACTIVE_RECONNECTING -> {
                val color = context.resolveColorAttr(android.R.attr.colorPrimary)
                Quadruple(color, R.drawable.ic_round_check_circle_24,
                    getString(R.string.activated),
                    getString(R.string.service_reconnecting))
            }
            ActivationStatus.CONNECTING -> {
                val color = context.resolveColorAttr(com.google.android.material.R.attr.colorSurfaceVariant)
                Quadruple(color, R.drawable.ic_about,
                    getString(R.string.connecting), null)
            }
            ActivationStatus.RECONNECTING -> {
                val color = context.resolveColorAttr(android.R.attr.colorError)
                Quadruple(color, R.drawable.ic_about,
                    getString(R.string.reconnecting), null)
            }
            ActivationStatus.DISCONNECTED -> {
                val color = context.resolveColorAttr(android.R.attr.colorError)
                Quadruple(color, R.drawable.ic_about,
                    getString(R.string.not_activated), null)
            }
        }

        val statusCard = requireView().findViewById<com.google.android.material.card.MaterialCardView>(R.id.status)
        statusCard.setCardBackgroundColor(colorAttr)
        binding.statusIcon.setImageDrawable(ContextCompat.getDrawable(context, iconRes))
        binding.statusTitle.text = titleText
        binding.statusSummary.text = buildString {
            append(getString(R.string.version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
            summaryExtra?.let { append(" · $it") }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setSystemInfo() {
        val context = requireContext()
        val contentResolver = context.contentResolver

        binding.apply {
            androidVersionValue.text = Build.VERSION.RELEASE
            sdkVersionValue.text = Build.VERSION.SDK_INT.toString()
            androidIdValue.text = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            brandValue.text = Build.MANUFACTURER
            modelValue.text = Build.MODEL
            skuValue.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SKU else ""
            typeValue.text = when (Build.TYPE) {
                "user" -> "Release"
                "userdebug" -> "Debug"
                "eng" -> "Engineering"
                else -> Build.TYPE
            }
            fingerValue.text = Build.FINGERPRINT
        }
    }

    private fun setHyperLinks() {
        val linkMovementMethod = LinkMovementMethod.getInstance()
        binding.apply {
            viewSource.movementMethod = linkMovementMethod
            viewSource.text = HtmlCompat.fromHtml(
                getString(R.string.about_view_source_code, "<b><a href=\"https://github.com/zjyzip/AdClose\">GitHub</a></b>"),
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )

            feedback.movementMethod = linkMovementMethod
            feedback.text = HtmlCompat.fromHtml(
                getString(R.string.join_telegram_channel, "<b><a href=\"https://t.me/AdClose\">Telegram</a></b>"),
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
        }
    }

    private fun initToolBar() {
        binding.toolbar.apply {
            title = getString(R.string.app_name)
            inflateMenu(R.menu.menu_home)

            setOnMenuItemClickListener {
                if (it.itemId == R.id.about) {
                    startActivity(Intent(requireContext(), AboutActivity::class.java))
                }
                true
            }

            val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: MotionEvent) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    startActivity(Intent(requireContext(), PerformanceActivity::class.java))
                }
            })

            setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                true
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
