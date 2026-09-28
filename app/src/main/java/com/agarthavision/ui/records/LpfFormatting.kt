package com.agarthavision.ui.records

import com.agarthavision.R
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.LpfDescriptor

/**
 * How an LPF range is written, in the one place both the screen and the PDF read it from.
 *
 * The range and its descriptor appear twice — Session Detail and the generated report — and a
 * medtech hands the second to a patient. Two formatters would eventually disagree about the
 * same session, which is the failure PB-17 spent a whole ticket undoing one layer down.
 */
val LpfDescriptor.labelRes: Int
    get() = when (this) {
        LpfDescriptor.RARE -> R.string.lpf_descriptor_rare
        LpfDescriptor.FEW -> R.string.lpf_descriptor_few
        LpfDescriptor.MODERATE -> R.string.lpf_descriptor_moderate
        LpfDescriptor.NUMEROUS -> R.string.lpf_descriptor_numerous
    }

/**
 * True when this species name is a binomial and renders italic (C11).
 *
 * "Hookworm" is a common name covering two genera, not a binomial, so italicising it would be
 * wrong in the one place typography carries meaning. Same rule as `RecordsScreen`.
 */
fun String.isBinomial(): Boolean = this != EggSpecies.HOOKWORM.displayName
