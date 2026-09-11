package com.agarthavision.domain.model

/**
 * Non-diagnostic WHO STH intensity tier derived from a species' EPG reading. Carries no
 * color/string presentation concerns — those live in the UI layer, keyed off this enum.
 *
 * This is an algorithmic indicator for reference only; it is not a medical diagnosis and
 * requires confirmation by a qualified health professional.
 */
enum class InfectivityLevel { LOW, MODERATE, EXTREME }
