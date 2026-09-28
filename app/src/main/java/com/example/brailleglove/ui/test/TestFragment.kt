package com.example.brailleglove.ui.test

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.brailleglove.BrailleGloveApp
import com.example.brailleglove.databinding.FragmentTestBinding
import com.example.brailleglove.utils.ViewModelFactory

class TestFragment : Fragment() {

    private var _binding: FragmentTestBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { requireActivity().application as BrailleGloveApp }

    private val viewModel: TestViewModel by viewModels {
        ViewModelFactory {
            TestViewModel(app.bleManager, app.speechManager, app.repository, app.soundEffects)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.initSpeech()
        setupObservers()

        binding.btnStartTest.setOnClickListener { viewModel.startTest() }
    }

    private fun setupObservers() {
        viewModel.testPhase.observe(viewLifecycleOwner) { phase ->
            binding.btnStartTest.isEnabled = phase != TestViewModel.TestPhase.RUNNING
            binding.btnStartTest.text = when (phase) {
                TestViewModel.TestPhase.IDLE -> "Start Test"
                TestViewModel.TestPhase.RUNNING -> "Test Running..."
                TestViewModel.TestPhase.COMPLETE -> "Try Again"
            }
        }

        viewModel.currentWord.observe(viewLifecycleOwner) { word ->
            binding.tvWord.text = word
        }

        viewModel.typedSoFar.observe(viewLifecycleOwner) { typed ->
            binding.tvTyped.text = typed
        }

        viewModel.statusMessage.observe(viewLifecycleOwner) { msg ->
            binding.tvStatus.text = msg
        }

        viewModel.finalAccuracy.observe(viewLifecycleOwner) { accuracy ->
            if (accuracy != null) {
                binding.tvAccuracy.visibility = View.VISIBLE
                binding.tvAccuracy.text = "Accuracy: ${accuracy.toInt()}%"
                com.example.brailleglove.utils.CelebrationAnimations.playCorrectPop(binding.tvAccuracy)
            } else {
                binding.tvAccuracy.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}