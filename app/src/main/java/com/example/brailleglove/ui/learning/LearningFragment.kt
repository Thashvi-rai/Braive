package com.example.brailleglove.ui.learning

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.brailleglove.BrailleGloveApp
import com.example.brailleglove.R
import com.example.brailleglove.databinding.FragmentLearningBinding
import com.example.brailleglove.utils.ViewModelFactory
import com.google.android.material.button.MaterialButton

class LearningFragment : Fragment() {

    private var _binding: FragmentLearningBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { requireActivity().application as BrailleGloveApp }

    private val viewModel: LearningViewModel by viewModels {
        ViewModelFactory {
            LearningViewModel(app.bleManager, app.speechManager, app.repository)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLearningBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.initSpeech {
            // TTS ready — could enable UI here if needed
        }

        setupLetterGrid()
        setupObservers()

        binding.btnListen.setOnClickListener {
            viewModel.listenForLetter()
        }
    }

    private fun setupLetterGrid() {
        val tileContext = android.view.ContextThemeWrapper(
            requireContext(), R.style.Widget_App_Button_LetterTile
        )
        val learningContainer = androidx.core.content.ContextCompat.getColor(
            requireContext(), R.color.mode_learning_container
        )
        val learningOnContainer = androidx.core.content.ContextCompat.getColor(
            requireContext(), R.color.mode_learning_on_container
        )
        ('A'..'Z').forEach { letter ->
            val btn = MaterialButton(tileContext, null, com.google.android.material.R.attr.materialButtonStyle).apply {
                text = letter.toString()
                backgroundTintList = android.content.res.ColorStateList.valueOf(learningContainer)
                setTextColor(learningOnContainer)
                setOnClickListener { viewModel.selectLetter(letter) }
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(4, 4, 4, 4)
            }
            binding.gridLetters.addView(btn, params)
        }
    }

    private fun setupObservers() {
        viewModel.currentLetter.observe(viewLifecycleOwner) { letter ->
            binding.tvCurrentLetter.text = letter.toString()
            com.example.brailleglove.utils.CelebrationAnimations.playCorrectPop(binding.tvCurrentLetter)
        }

        viewModel.dotPattern.observe(viewLifecycleOwner) { pattern ->
            // Update the 6 dot views — true = active (filled), false = inactive
            val dotViews = listOf(
                binding.dot0, binding.dot1, binding.dot2,
                binding.dot3, binding.dot4, binding.dot5
            )
            dotViews.forEachIndexed { index, dotView ->
                dotView.setBackgroundResource(
                    if (pattern[index]) R.drawable.dot_active else R.drawable.dot_inactive
                )
            }
        }

        viewModel.statusMessage.observe(viewLifecycleOwner) { msg ->
            binding.tvStatus.text = msg
        }

        viewModel.isListening.observe(viewLifecycleOwner) { listening ->
            binding.btnListen.text = if (listening) "Listening..." else "Say a Letter 🎤"
            binding.btnListen.isEnabled = !listening
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}