# Voice Recorder Wear OS

A modern voice recording app for Wear OS with AI processing capabilities.

## Features

### Modern Wear OS UI
- **Material3 Design**: Clean, modern interface using Material3 components
- **Round Screen Optimized**: Layout designed specifically for round Wear OS displays
- **Dynamic Spacing**: Proper padding and spacing for small screens
- **Dark Theme**: Optimized dark theme with proper color contrast

### Recording Interface
- **Centered Mic Button**: Large, easily tappable circular recording button
- **Pulsing Animation**: Visual feedback with pulsing animation during recording
- **Timer Display**: Real-time recording timer (MM:SS format)
- **Waveform Visualization**: Animated waveform bars showing recording activity
- **Haptic Feedback**: Tactile feedback on button interactions

### Action Controls
- **Save/Delete Buttons**: Chip-style action buttons for managing recordings
- **Accessibility**: Proper content descriptions for screen readers
- **Visual States**: Clear visual feedback for different app states

### Technical Features
- **Jetpack Compose**: Modern declarative UI framework
- **Wear OS Components**: Uses `ScalingLazyColumn`, `TimeText`, and other Wear-specific components
- **Animation**: Smooth animations using Compose animation APIs
- **Permission Handling**: Proper audio recording permission management

## UI Components

### Main Screen Layout
- `Scaffold` with `TimeText` at the top
- `ScalingLazyColumn` for scrollable content
- Proper content padding for round screens

### Recording Button
- Circular design with pulsing animation
- Mic icon for recording, Stop icon when active
- Large touch target (120dp) for easy interaction
- Color changes based on recording state

### Timer Display
- Bold typography for easy reading
- Color changes to indicate recording state
- Real-time updates during recording

### Waveform Visualization
- 5 animated bars showing recording activity
- Staggered animation timing for realistic effect
- Color changes based on recording state

### Action Buttons
- Chip-style buttons with icons and labels
- Save (blue) and Delete (red) options
- Only visible when recording is complete

## Color Scheme
- **Primary**: Light blue (#64B5F6) for active states
- **Surface**: Dark gray (#121212) for background
- **Error**: Red (#EF5350) for delete actions
- **Surface Variant**: Medium gray (#1E1E1E) for secondary elements

## Accessibility
- Content descriptions for all interactive elements
- Proper contrast ratios for text and icons
- Large touch targets for easy interaction
- Haptic feedback for user actions

## Dependencies
- `androidx.wear.compose:compose-material:1.2.1`
- `androidx.wear.compose:compose-foundation:1.2.1`
- `androidx.compose.material3:material3:1.1.2`

## Building
The app requires Android SDK 26+ and targets Wear OS devices with round displays. 