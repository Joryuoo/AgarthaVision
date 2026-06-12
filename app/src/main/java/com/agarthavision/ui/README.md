# UI Package

## Purpose

The `ui` package contains the presentation layer of the AgarthaVision Android app.

This includes Jetpack Compose screens, ViewModels, navigation, theme setup, and reusable UI components.

## Responsibilities

The `ui` package contains:

- Compose screens
- ViewModels
- UI state classes
- UI events
- Navigation graph
- Theme files
- Reusable custom composables
- Feature-specific UI folders

## Current Subpackages

```text
ui/
├── capture/         # Capture screen, ViewModel, and connection-loss banner
├── components/      # Shared Agartha components and microscopy primitives
├── dashboard/       # Dashboard screen and ViewModel
├── login/           # Login screen and ViewModel
├── navigation/      # Navigation graph and routes
├── records/         # Records, session detail, sample detail, and reports UI
├── sessions/        # Session picker/list screen and ViewModel
├── settings/        # Settings placeholder
├── theme/           # AppColors, typography, spacing, and MaterialTheme setup
└── verify/          # Verification queue, verification sheet, and manual sheet
```
