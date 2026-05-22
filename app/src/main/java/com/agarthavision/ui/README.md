
## `ui/README.md`

```markdown
# UI Package

## Purpose

The `ui` package contains the presentation layer of the AgarthaVision Android app.

This includes Jetpack Compose screens, ViewModels, navigation, theme setup, and reusable UI components.

## Responsibilities

The `ui` package may contain:

- Compose screens
- ViewModels
- UI state classes
- UI events
- Navigation graph
- Theme files
- Reusable custom composables
- Feature-specific UI folders

## Planned Subpackages

```text
ui/
├── theme/           # AgarthaLightColors, AgarthaRadius, AgarthaTypography, AgarthaVisionTheme
├── navigation/      # AppNavHost, route definitions, navigation graph
├── components/      # Shared custom composables
├── capture/         # CaptureScreen, CaptureViewModel
├── queue/           # QueueScreen, QueueViewModel
├── validate/        # ValidateScreen, ValidateViewModel
├── reports/         # ReportsScreen, ReportsViewModel, AdminDashboard
└── settings/        # SettingsScreen, SettingsViewModel