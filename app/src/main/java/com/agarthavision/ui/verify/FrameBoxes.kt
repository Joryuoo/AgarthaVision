package com.agarthavision.ui.verify

import com.agarthavision.domain.inference.ImageBox
import com.agarthavision.domain.usecase.verify.Finding

/**
 * One rectangle the Verification Screen draws on the frame, and how to draw it.
 *
 * The screen used to draw `frame.predictions` directly, which is why every box the medtech
 * placed by hand was invisible: a replacement for a box the model got wrong, and every egg
 * located through Add Species, live on the *answers* and never reached the renderer. Worse than
 * missing — a replaced box left the model's original rectangle on screen in its wrong place,
 * looking authoritative, so correcting a box appeared to do nothing at all.
 *
 * Derived from the findings rather than the predictions because the findings are the only place
 * that knows all three things at once: what geometry to draw, whose it is, and whether the
 * medtech has since disowned it.
 *
 * @property trusted false when the medtech has said this rectangle is wrong and nothing has
 *   replaced it. Drawn dashed — see [FrameWithBoxes]. It is a statement about the *rectangle*,
 *   not about the egg: a box called misplaced still holds a real egg that still gets counted.
 * @property active true for the one box the screen is currently about — the detection being
 *   questioned, or the egg a draw is being started for. Everything else recedes to gray.
 */
internal data class FrameBox(
    val box: ImageBox,
    val trusted: Boolean,
    val active: Boolean,
)

/**
 * Every box to draw on this frame, in the order they are drawn.
 *
 * Pure, and kept out of the composable for the same reason [frameTransform] is: the rules below
 * have wrong answers that still look plausible on a device, and a unit test is far cheaper than
 * finding one over a microscope.
 *
 * The rules, stated once:
 *
 * | Row | On the frame |
 * |---|---|
 * | model box, kept, placed right | the model's box, solid |
 * | model box, called misplaced, not redrawn | the model's box, **dashed** |
 * | model box, called misplaced, redrawn | the drawn box, solid — the model's is *hidden* |
 * | model box, rejected as not an egg | the model's box, **dashed** |
 * | added species, egg located | the drawn box, solid |
 * | added species, egg not located | nothing, and that is not an error |
 *
 * A replaced box hides the model's rather than showing both, because two rectangles on one egg
 * read as two eggs.
 *
 * **Every drawn box is emitted, even past the species' current slot count.** A typed total below
 * what the frame already holds is a contradiction submit refuses (`totalsAreConsistent`), and
 * during the keystrokes that get there a box would otherwise blink out of existence — which is
 * the exact failure this ticket exists to end.
 *
 * @param active which box the screen is currently about, or null to dim every box — what
 *   drawing does, so the rectangle under the medtech's finger is the only coloured one.
 */
internal fun List<Finding>.frameBoxes(active: DrawTarget?): List<FrameBox> =
    flatMapIndexed { index, finding ->
        val answers = finding.answers
        val prediction = finding.prediction
        if (prediction == null) {
            answers.drawnBoxes.mapIndexed { slot, box ->
                FrameBox(
                    box = box,
                    // Nobody draws a box they disagree with, and there is no model claim here
                    // to contradict. A located egg is always the medtech's own word.
                    trusted = true,
                    active = active?.findingIndex == index && active.slot == slot,
                )
            }
        } else {
            val replacement = answers.drawnBox
            listOf(
                FrameBox(
                    box = replacement ?: ImageBox(
                        x = prediction.x,
                        y = prediction.y,
                        width = prediction.width,
                        height = prediction.height,
                    ),
                    // Rejecting the detection outranks replacing its box: a medtech who redraws
                    // and then unchecks "there is an egg here" has disowned the whole row, and
                    // drawing their own rectangle solid would say the opposite. An unanswered
                    // box stays solid — it is the model's claim, not yet adjudicated, and
                    // dashing it would report a rejection nobody made.
                    trusted = when {
                        answers.isEgg == false -> false
                        replacement != null -> true
                        else -> answers.isBoxCorrect != false
                    },
                    active = active?.findingIndex == index && active.slot == null,
                )
            )
        }
    }
