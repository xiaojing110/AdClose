package com.close.hook.ads.ui.fragment.cloud

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.close.hook.ads.R
import com.close.hook.ads.databinding.DialogAddSubscriptionBinding
import com.close.hook.ads.databinding.FragmentCloudRuleBinding
import com.close.hook.ads.ui.adapter.CloudRuleAdapter
import com.close.hook.ads.ui.fragment.base.BaseFragment
import com.close.hook.ads.ui.viewmodel.CloudRuleViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CloudRuleFragment : BaseFragment<FragmentCloudRuleBinding>() {

    private val viewModel by viewModels<CloudRuleViewModel>()
    private lateinit var adapter: CloudRuleAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initToolbar()
        initRecyclerView()
        initButtons()
        observeData()

        viewModel.initDefaults()
    }

    private fun initToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().finish()
        }
    }

    private fun initRecyclerView() {
        adapter = CloudRuleAdapter(
            requireContext(),
            onUpdate = { sub -> viewModel.forceUpdate(sub.id) },
            onToggle = { sub -> viewModel.toggleEnabled(sub) },
            onDelete = { sub ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.delete_subscription))
                    .setMessage(getString(R.string.delete_subscription_confirm, sub.name))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        viewModel.deleteSubscription(sub)
                    }
                    .show()
            },
            onIntervalChange = { sub, interval -> viewModel.updateInterval(sub, interval) }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun initButtons() {
        binding.btnUpdateAll.setOnClickListener {
            viewModel.forceUpdateAll()
        }

        binding.btnAddSubscription.setOnClickListener {
            showAddSubscriptionDialog()
        }
    }

    private fun showAddSubscriptionDialog() {
        val dialogBinding = DialogAddSubscriptionBinding.inflate(LayoutInflater.from(requireContext()))

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.add_subscription_title))
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.add_subscription)) { _, _ ->
                val name = dialogBinding.editName.text?.toString()?.trim() ?: ""
                val url = dialogBinding.editUrl.text?.toString()?.trim() ?: ""
                val intervalStr = dialogBinding.editInterval.text?.toString()?.trim() ?: "12"
                val interval = intervalStr.toIntOrNull() ?: 12

                if (name.isEmpty() || url.isEmpty()) {
                    Toast.makeText(requireContext(), getString(R.string.name_url_required), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    Toast.makeText(requireContext(), getString(R.string.invalid_url), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                viewModel.addSubscription(name, url, interval)
            }
            .show()
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.subscriptions.collectLatest { list ->
                        adapter.submitList(list)
                    }
                }

                launch {
                    viewModel.updateProgress.collectLatest { progress ->
                        if (progress != null) {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.progressText.visibility = View.VISIBLE
                            binding.progressText.text = progress.message
                        } else {
                            binding.progressBar.visibility = View.GONE
                            binding.progressText.visibility = View.GONE
                        }
                    }
                }

                launch {
                    viewModel.toastMessage.collectLatest { msg ->
                        if (msg != null) {
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                            viewModel.clearToast()
                        }
                    }
                }
            }
        }
    }
}
