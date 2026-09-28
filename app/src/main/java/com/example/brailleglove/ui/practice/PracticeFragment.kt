package com.example.brailleglove.ui.practice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.brailleglove.BrailleGloveApp
import com.example.brailleglove.R
import com.example.brailleglove.databinding.FragmentPracticeBinding
import com.example.brailleglove.utils.ViewModelFactory

class PracticeFragment : Fragment() {

    private var _binding: FragmentPracticeBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { requireActivity().application as BrailleGloveApp }

    private val viewModel: PracticeViewModel by viewModels {
        ViewModelFactory {
            PracticeViewModel(app.bleManager, app.speechManager, app.repository, app.soundEffects)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPracticeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.initSpeech()
        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        viewModel.gamePhase.observe(viewLifecycleOwner) { phase ->
            // Show/hide panels based on game phase
            binding.panelLevelSelect.visibility =
                if (phase == PracticeViewModel.GamePhase.LEVEL_SELECT) View.VISIBLE else View.GONE
            binding.panelGame.visibility =
                if (phase != PracticeViewModel.GamePhase.LEVEL_SELECT) View.VISIBLE else View.GONE

            binding.btnNext.visibility = when (phase) {
                PracticeViewModel.GamePhase.RESULT_CORRECT,
                PracticeViewModel.GamePhase.RESULT_WRONG -> View.VISIBLE
                else -> View.GONE
            }
            binding.btnStart.visibility =
                if (phase == PracticeViewModel.GamePhase.WAITING) View.VISIBLE else View.GONE

            binding.btnRetry.visibility = when (phase) {
                PracticeViewModel.GamePhase.GAME_OVER,
                PracticeViewModel.GamePhase.LEVEL_COMPLETE -> View.VISIBLE
                else -> View.GONE
            }

            when (phase) {
                PracticeViewModel.GamePhase.RESULT_CORRECT -> {
                    com.example.brailleglove.utils.CelebrationAnimations.playCorrectPop(binding.tvStatus)
                    com.example.brailleglove.utils.CelebrationAnimations.playStarBurst(
                        binding.burstOverlay,
                        binding.tvStatus.x + binding.tvStatus.width / 2f,
                        binding.tvStatus.y,
                        starCount = 8
                    )
                }
                PracticeViewModel.GamePhase.RESULT_WRONG -> {
                    com.example.brailleglove.utils.CelebrationAnimations.playWrongShake(binding.tvStatus)
                }
                PracticeViewModel.GamePhase.LEVEL_COMPLETE -> {
                    com.example.brailleglove.utils.CelebrationAnimations.playStarBurst(
                        binding.burstOverlay,
                        binding.practiceRoot.width / 2f,
                        binding.practiceRoot.height / 2f,
                        starCount = 24
                    )
                }
                else -> Unit
            }
        }

        viewModel.currentQuestion.observe(viewLifecycleOwner) { q ->
            q ?: return@observe
            binding.tvQuestionType.text = when (q.type) {
                com.example.brailleglove.data.model.Question.Type.VIBRATE_TO_SPEECH ->
                    "Feel the vibration → say the letter"
                com.example.brailleglove.data.model.Question.Type.SPEECH_TO_TAP ->
                    "Listen to the letter → tap the dots"
            }
        }

        viewModel.questionNumber.observe(viewLifecycleOwner) { num ->
            val total = viewModel.totalQuestions.value ?: com.example.brailleglove.utils.Constants.QUESTIONS_PER_LEVEL
            binding.tvQuestionNumber.text = "Question $num / $total"
        }

        viewModel.totalQuestions.observe(viewLifecycleOwner) { total ->
            val num = viewModel.questionNumber.value ?: 1
            binding.tvQuestionNumber.text = "Question $num / $total"
        }

        viewModel.livesRemaining.observe(viewLifecycleOwner) { lives ->
            binding.tvLives.text = "❤️".repeat(lives.coerceAtLeast(0))
        }

        viewModel.score.observe(viewLifecycleOwner) { s ->
            binding.tvScore.text = "Score: $s"
        }

        viewModel.statusMessage.observe(viewLifecycleOwner) { msg ->
            binding.tvStatus.text = msg
        }

        viewModel.allLevels.observe(viewLifecycleOwner) { levels ->
            // Populate level buttons dynamically
            binding.levelButtonContainer.removeAllViews()
            val ctx = requireContext()
            levels.forEach { level ->
                val stars = if (level.isCompleted) " " + "★".repeat(level.stars) else ""
                val btn = com.google.android.material.button.MaterialButton(
                    ctx, null, com.google.android.material.R.attr.materialButtonStyle
                ).apply {
                    text = if (level.isUnlocked) "${level.title}$stars" else "🔒 ${level.title}"
                    isEnabled = level.isUnlocked
                    cornerRadius = 16
                    if (level.isUnlocked) {
                        backgroundTintList = android.content.res.ColorStateList.valueOf(
                            androidx.core.content.ContextCompat.getColor(ctx, R.color.mode_practice)
                        )
                        setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.mode_practice_on))
                    } else {
                        backgroundTintList = android.content.res.ColorStateList.valueOf(
                            androidx.core.content.ContextCompat.getColor(ctx, R.color.brand_surface_variant)
                        )
                        setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.brand_ink_soft))
                    }
                    setOnClickListener { viewModel.selectLevel(level) }
                }
                val params = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 16 }
                binding.levelButtonContainer.addView(btn, params)
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnStart.setOnClickListener { viewModel.startQuestion() }
        binding.btnNext.setOnClickListener { viewModel.nextQuestion() }
        binding.btnRetry.setOnClickListener { viewModel.resetToLevelSelect() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}