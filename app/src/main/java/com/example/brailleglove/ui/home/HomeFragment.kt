package com.example.brailleglove.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.brailleglove.BrailleGloveApp
import com.example.brailleglove.R
import com.example.brailleglove.ble.BleManager.ConnectionState
import com.example.brailleglove.databinding.FragmentHomeBinding
import com.example.brailleglove.utils.ViewModelFactory

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { requireActivity().application as BrailleGloveApp }

    private val viewModel: HomeViewModel by viewModels {
        ViewModelFactory { HomeViewModel(app.bleManager, app.repository) }
    }

    // Permission launcher — requests BLE + Audio permissions together
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Buttons are already enabled. Just update status text if BLE was denied.
        if (!results.values.all { it }) {
            binding.tvBleStatus.text = "Some permissions denied — glove features limited"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
        setupClickListeners()
        checkPermissions()
    }

    private fun setupObservers() {
        viewModel.connectionState.observe(viewLifecycleOwner) { state ->
            when (state) {
                ConnectionState.CONNECTED -> {
                    binding.tvBleStatus.text = "Glove Connected ✓"
                    binding.btnConnect.text = "Disconnect"
                    binding.btnConnect.isEnabled = true
                }
                ConnectionState.SCANNING -> {
                    binding.tvBleStatus.text = "Scanning..."
                    binding.btnConnect.isEnabled = false
                }
                ConnectionState.CONNECTING -> {
                    binding.tvBleStatus.text = "Connecting..."
                    binding.btnConnect.isEnabled = false
                }
                ConnectionState.DISCONNECTED -> {
                    binding.tvBleStatus.text = "Glove Not Connected"
                    binding.btnConnect.text = "Connect Glove"
                    binding.btnConnect.isEnabled = true
                    binding.tvBattery.visibility = View.GONE
                }
                else -> {}
            }
        }

        viewModel.isBatteryLow.observe(viewLifecycleOwner) { isLow ->
            binding.tvBattery.visibility = if (isLow == true) View.VISIBLE else View.GONE
            if (isLow == true) {
                binding.tvBattery.text = getString(R.string.battery_low_warning)
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnConnect.setOnClickListener {
            if (viewModel.connectionState.value == ConnectionState.CONNECTED) {
                viewModel.disconnect()
            } else {
                viewModel.startScan()
            }
        }

        binding.btnLearning.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_learning)
        }

        binding.btnPractice.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_practice)
        }

        binding.btnTest.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_test)
        }
    }

    private fun checkPermissions() {
        val required = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        val missing = required.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) !=
                    PackageManager.PERMISSION_GRANTED
        }

        // Always enable mode buttons so the app is navigable (e.g. on emulator).
        // BLE features simply won't work without the glove connected.
        enableModeButtons()

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun enableModeButtons() {
        binding.btnLearning.isEnabled = true
        binding.btnPractice.isEnabled = true
        binding.btnTest.isEnabled = true
    }

    private fun disableModeButtons() {
        binding.btnLearning.isEnabled = false
        binding.btnPractice.isEnabled = false
        binding.btnTest.isEnabled = false
    }

    private fun showPermissionDeniedMessage() {
        binding.tvBleStatus.text = "Permissions required to use the app"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}