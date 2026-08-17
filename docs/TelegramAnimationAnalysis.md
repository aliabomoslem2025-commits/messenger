# Telegram Android Animation Analysis

## Reference Repository
https://github.com/DrKLO/Telegram

## Analysis Methodology

This document analyzes the animation philosophy and implementation patterns from Telegram Android's source code.
The goal is to extract **motion principles** and **timing behaviors**, NOT to copy code.

All implementations will be rebuilt using:
- Jetpack Compose
- Compose Animation APIs
- Canvas API for custom drawing
- Coroutines + StateFlow
- Lottie Compose for animated stickers

---

## 1. Motion Philosophy

### Core Principles

1. **Speed & Responsiveness**
   - Fast animations (120-280ms) for UI feedback
   - No perceived delay between user action and visual response
   - Animations should feel "instant" but not jarring

2. **Natural Easing**
   - Heavy use of cubic bezier curves with overshoot
   - Entrance: Fast out, slow in (decelerate)
   - Exit: Slow out, fast in (accelerate)
   - Emotional animations: Spring-based with damping

3. **Layered Animation**
   - Multiple properties animate simultaneously (scale + alpha + translation)
   - Each layer has slightly different timing
   - Creates depth and richness without complexity

4. **Context-Aware Timing**
   - Message send: Fast (~150ms)
   - Message appear: Normal (~250ms)
   - Sticker emotion: Slow/emotional (~600ms)
   - Transitions: Smooth (~300ms)

5. **Micro-interactions**
   - Button presses have subtle scale feedback
   - Icons morph rather than swap
   - Counters animate digits, not just update text
   - Every interaction has visual feedback

---

## 2. Key Animation Patterns

### 2.1 Message Arrival Animation

**Purpose:** New message appears in chat

**Visual Behavior:**
```
Initial state:
  - alpha = 0
  - scale = 0.92
  - translationY = +40dp

Animation sequence:
  1. Alpha: 0 → 1 (over 250ms)
  2. Scale: 0.92 → 1.04 → 1.0 (overshoot, spring)
  3. Translation: 40dp → 0 (over 250ms)
```

**Timing:**
- Duration: 250ms
- Easing: FastOutSlowIn
- Spring damping: ~0.65
- Overshoot: ~4% scale

**Compose Implementation Strategy:**
```kotlin
animateFloatAsState(
    targetValue = if (isVisible) 1f else 0.92f,
    animationSpec = spring(
        dampingRatio = 0.65f,
        stiffness = Spring.StiffnessLow
    )
)
```

---

### 2.2 Message Send Animation

**Purpose:** User sends a message, button transforms

**Visual Behavior:**
```
Send button state machine:
  Idle → Pressed → Morphing → Sent

Layers:
  1. Button scale: 1.0 → 0.9 → 1.0 (press feedback)
  2. Icon rotation: 0° → 360° (morph transition)
  3. Icon alpha: 1 → 0 → 1 (crossfade)
  4. Message bubble: scale 0.92 → 1.0, alpha 0 → 1
```

**Timing:**
- Button press: 150ms
- Icon morph: 150ms
- Message appear: 250ms
- Total sequence: ~400ms

**Compose Implementation Strategy:**
- Use `AnimatedContent` for icon crossfade
- Use `rotate` modifier with `animateFloatAsState`
- Chain animations with `LaunchedEffect` and delays

---

### 2.3 Message Delete Animation (Particle Effect)

**Purpose:** Message destruction with visual flair

**Visual Behavior:**
```
Sequence:
  1. Freeze frame (50ms)
  2. Bubble cracks / fragments generate
  3. Particles separate with velocity
  4. Gravity applied to fragments
  5. Alpha fade out
  6. Layout collapses
```

**Particle Properties:**
```kotlin
data class Particle(
    val position: Offset,      // Starting position
    val velocity: Offset,      // Direction + speed
    val rotation: Float,       // Angular velocity
    val rotationSpeed: Float,  // Degrees per frame
    val scale: Float,          // Size multiplier
    val alpha: Float,          // Transparency
    val lifetime: Long,        // How long to live
    val gravity: Float         // Downward acceleration
)
```

**Physics:**
- Initial velocity: Random direction, magnitude 200-400 dp/s
- Gravity: 500 dp/s²
- Lifetime: 400-600ms
- Fragment count: 8-16 pieces

**Compose Implementation Strategy:**
- Custom `Canvas` composable for particle rendering
- `Animatable` for each particle property
- Update loop with `withFrameNanos`
- Remove particles when lifetime expires

---

### 2.4 Typing Indicator Animation

**Purpose:** Show that someone is typing

**Visual Behavior:**
```
Three dots animate sequentially:
  ●   ●   ●
  
Wave pattern:
  Dot 1: moves up at t=0
  Dot 2: moves up at t=80ms
  Dot 3: moves up at t=160ms
  
Each dot:
  - Moves vertically ±4dp
  - Alpha pulses slightly
  - Infinite repeating animation
```

**Timing:**
- Dot duration: 250ms per cycle
- Delay between dots: 80ms
- Total cycle: ~500ms before repeat

**Compose Implementation Strategy:**
```kotlin
@Composable
fun TypingIndicator() {
    Row {
        repeat(3) { index ->
            val offset by infiniteRepeatable(
                animation = tween(250, delayMillis = index * 80),
                initialValue = 0f,
                targetValue = -4f
            )
            Box(modifier = Modifier.offset(y = offset.dp)) {
                Dot()
            }
        }
    }
}
```

---

### 2.5 Reaction Animation

**Purpose:** User adds reaction to message

**Visual Behavior:**
```
Sequence:
  1. User taps emoji in picker
  2. Emoji scales up (1.0 → 1.3)
  3. Emoji "flies" toward message position
  4. Small bounce on arrival
  5. Attaches to message bubble
  6. Counter animates: 10 → 11
```

**Counter Animation:**
```
Old number slides up and fades out
New number slides up from below and fades in

Duration: 200ms
Easing: FastOutSlowIn
```

**Compose Implementation Strategy:**
- Use `AnimatedContent` with slide + fade transitions
- Track emoji position with `LayoutCoordinates`
- Animate along path using `Offset` animation
- Counter uses `AnimatedVisibility` for number transition

---

### 2.6 Sticker Emotion System

**Purpose:** Animated stickers convey emotion through motion

**Emotion Types:**

#### Happy/Laugh
```
Motion:
  - Small rotation shake (±5°)
  - Body bounce (scale pulse 1.0 → 1.05 → 1.0)
  - Horizontal sway
  
Timing:
  - Rotation: 150ms left, 150ms right, 150ms center
  - Bounce: 300ms cycle
  - Continuous looping
```

#### Cry
```
Motion:
  - Slow breathing (subtle scale pulse)
  - Vertical bobbing
  - Tear particles fall with gravity
  
Particles:
  - Spawn rate: 1 tear per 800ms
  - Fall duration: 600ms
  - Fade out at bottom
```

#### Angry
```
Motion:
  - Fast vibration (rapid small movements)
  - Impact shake (sudden position jump)
  - Red flash overlay
  
Timing:
  - Vibration frequency: 15Hz
  - Amplitude: 2-4dp
  - Flash duration: 100ms every 2s
```

#### Love
```
Motion:
  - Floating hearts rise from sticker
  - Scale pulse (slow, romantic)
  - Gentle rotation
  
Particles:
  - Heart emojis spawn at bottom
  - Rise with sine wave horizontal movement
  - Fade out after 1000ms
  - Spawn rate: 1 per 400ms
```

#### Surprise
```
Motion:
  - Quick zoom in (overshoot)
  - Small bounce back
  - Explosion particles outward
  
Timing:
  - Zoom: 150ms
  - Bounce: 200ms
  - Particles: instant spawn, 400ms lifetime
```

**Compose Implementation Strategy:**
```kotlin
enum class StickerEmotion {
    HAPPY, LAUGH, CRY, ANGRY, LOVE, SURPRISE, FEAR
}

@Composable
fun AnimatedSticker(
    emotion: StickerEmotion,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = when (emotion) {
            StickerEmotion.HAPPY -> 1.05f
            StickerEmotion.CRY -> 0.98f
            else -> 1.0f
        },
        animationSpec = infiniteRepeatable(
            tween(600),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    // Render sticker with emotion-specific particles
}
```

---

### 2.7 Voice Message Animation

**Purpose:** Visual feedback for voice recording/playback

**States:**

#### Recording
```
Visual:
  - Microphone icon pulses
  - Waveform animates based on amplitude
  - Timer counts up
  - Red indicator grows/shrinks
  
Waveform:
  - 40-60 bars
  - Height based on audio amplitude
  - Smooth interpolation between samples
  - Latest bar always at right edge
```

#### Playing
```
Visual:
  - Waveform plays from left to right
  - Progress indicator moves across waveform
  - Played portion changes color
  - Pause/Play button morphs
```

**Timing:**
- Pulse duration: 1000ms
- Waveform update: every 50ms
- Progress: real-time sync with playback

**Compose Implementation Strategy:**
- Custom Canvas for waveform drawing
- `Animatable` for progress
- Amplitude data from MediaRecorder/AudioRecord
- Path drawing with bezier curves for smooth waveform

---

### 2.8 Input Button Transition

**Purpose:** Microphone ↔ Send icon transition

**Visual Behavior:**
```
Empty input → Microphone visible
Text input → Send icon visible

Transition:
  1. Current icon scales down (1.0 → 0.7)
  2. Alpha fades (1.0 → 0)
  3. New icon appears at small scale (0.7)
  4. New icon scales up (0.7 → 1.0)
  5. Optional: 360° rotation during transition
```

**Timing:**
- Total duration: 150ms
- Scale animation: 150ms
- Alpha crossfade: 100ms
- Rotation (if used): 150ms full rotation

**Compose Implementation Strategy:**
```kotlin
@Composable
fun InputButton(hasText: Boolean) {
    AnimatedContent(
        targetState = hasText,
        transitionSpec = {
            rotateIn() togetherWith rotateOut()
        }
    ) { showingSend ->
        Icon(
            imageVector = if (showingSend) Icons.Default.Send else Icons.Default.Mic,
            contentDescription = if (showingSend) "Send" else "Voice"
        )
    }
}
```

---

### 2.9 Media Viewer Transition

**Purpose:** Thumbnail → Fullscreen expansion

**Visual Behavior:**
```
Open:
  1. Thumbnail captured as starting bounds
  2. Image scales from thumbnail size to fullscreen
  3. Background fades from transparent to scrim
  4. Toolbar slides in from top/bottom
  
Close:
  Reverse of open animation
```

**Shared Element Properties:**
- Scale: Based on size ratio between thumbnail and fullscreen
- Alpha: Crossfade during scale
- Clip: Rounded corners during transition

**Timing:**
- Scale duration: 300ms
- Background fade: 200ms
- Toolbar slide: 250ms

**Compose Implementation Strategy:**
- Use `SharedElementTransition` (or custom implementation)
- Capture thumbnail bounds with `onGloballyPositioned`
- Animate scale based on size ratio
- Use `graphicsLayer` for performance

---

### 2.10 Chat List Item Animation

**Purpose:** Subtle feedback when list items update

**Animate On:**
- Unread badge count change (scale pulse)
- Last message preview text change (fade)
- Online status change (color transition)
- Typing indicator appearance (slide + fade)

**DO NOT Animate:**
- Entire list item on every update (performance)
- Avatar image (unnecessary)
- Static content

**Compose Implementation Strategy:**
```kotlin
@Composable
fun ChatListItem(chat: ChatUiModel) {
    // Only animate changing properties
    val badgeScale by animateFloatAsState(
        targetValue = if (chat.hasNewUnread) 1.2f else 1f
    )
    
    AnimatedVisibility(
        visible = chat.isTyping,
        enter = fadeIn() + slideInVertically()
    ) {
        TypingIndicator()
    }
}
```

---

## 3. Performance Guidelines

### Frame Budget
- Target: 60 FPS (16.67ms per frame)
- Animation work must complete within frame budget
- Use `withFrameNanos` for precise timing

### Optimization Strategies

1. **Pause Invisible Animations**
   - Stop animating composables not on screen
   - Use `DisposableEffect` to cleanup

2. **Avoid Allocations in Animation Loop**
   - Reuse objects where possible
   - Pre-calculate values outside frame callback

3. **Use Appropriate Animation APIs**
   - Simple properties: `animate*AsState`
   - Complex sequences: `Animatable`
   - Physics-based: `spring`
   - Timed: `tween`

4. **Canvas vs Compose Primitives**
   - Many particles: Canvas (single draw call)
   - Few elements: Compose primitives (easier state)

5. **Derived State for Expensive Calculations**
   ```kotlin
   val visibleMessages by derivedStateOf {
       messages.filter { it.isVisible }
   }
   ```

---

## 4. Haptic Feedback Integration

**Principle:** Visual + Haptic = Premium Feel

**Patterns:**

| Action | Haptic Pattern |
|--------|----------------|
| Message sent | Light click (VibrationEffect.EFFECT_TICK) |
| Reaction added | Medium click |
| Voice recording start | Heavy click |
| Voice recording end | Double click |
| Message delete | Long press haptic |
| Pull to refresh | Ratchet effect |

**Compose Implementation:**
```kotlin
@Composable
fun rememberHapticFeedback(): HapticFeedback {
    val context = LocalContext.current
    return remember { HapticFeedback(context) }
}

class HapticFeedback(private val context: Context) {
    private val vibrator = context.getSystemService(VibratorService::class.java)
    
    fun tick() {
        vibrator.vibrate(VibrationEffect.createPredefined(EFFECT_TICK))
    }
    
    fun mediumClick() {
        vibrator.vibrate(VibrationEffect.createPredefined(EFFECT_CLICK))
    }
}
```

---

## 5. Implementation Priority

### Phase 1: Core Motion Engine
- [x] MatrixMotion.kt (already exists)
- [ ] AnimationController.kt
- [ ] AnimationScheduler.kt
- [ ] SpringSpecs.kt
- [ ] EasingCurves.kt

### Phase 2: Message Animations
- [ ] MessageEnterAnimation.kt
- [ ] MessageSendAnimation.kt
- [ ] MessageDeleteAnimation.kt
- [ ] MessageGroupAnimation.kt

### Phase 3: Sticker Engine
- [ ] StickerAnimationEngine.kt
- [ ] StickerEmotion.kt
- [ ] StickerParticleEffect.kt
- [ ] Lottie integration

### Phase 4: Reaction System
- [ ] ReactionEngine.kt
- [ ] ReactionFlyAnimation.kt
- [ ] CounterAnimation.kt
- [ ] ReactionPicker.kt

### Phase 5: Particle System
- [ ] ParticleSystem.kt
- [ ] Particle.kt
- [ ] ParticleEmitter.kt
- [ ] ConfettiEffect.kt
- [ ] HeartEffect.kt

### Phase 6: Transitions
- [ ] ScreenTransitions.kt
- [ ] SharedElementTransition.kt
- [ ] NavTransitions.kt

### Phase 7: Voice & Input
- [ ] VoiceRecordingAnimation.kt
- [ ] WaveformRenderer.kt
- [ ] InputButtonTransition.kt

### Phase 8: Haptics
- [ ] HapticFeedbackManager.kt
- [ ] HapticPatterns.kt

---

## 6. Testing Strategy

### Preview Tests
Create `@Preview` composables for each animation:
- MessageEnterPreview
- MessageDeletePreview
- StickerEmotionPreview
- ReactionAnimationPreview
- VoiceWaveformPreview

### Manual Testing Checklist
- [ ] Dark mode
- [ ] Light mode
- [ ] RTL layout
- [ ] Large font scaling
- [ ] Low-end device (throttle CPU)
- [ ] Long chat lists (scroll performance)
- [ ] Rapid interactions (spam clicks)

### Performance Metrics
- Frame rendering time < 16ms
- No dropped frames during scroll
- Memory stable during long sessions
- Battery impact minimal

---

## 7. Key Takeaways

1. **Speed matters more than complexity** - Fast, simple animations feel better than slow, complex ones

2. **Overshoot adds life** - Slight overshoot (4-8%) makes animations feel natural

3. **Layer multiple properties** - Scale + alpha + translation together creates depth

4. **Context-aware timing** - Different animations need different speeds

5. **Haptics amplify visuals** - Combine haptic feedback with visual animations

6. **Performance is non-negotiable** - Drop any animation that impacts 60 FPS

7. **Emotion through motion** - Stickers and reactions should feel alive

---

## References

- Telegram Android Source: https://github.com/DrKLO/Telegram
- Material Motion: https://material.io/design/motion/
- Compose Animation Docs: https://developer.android.com/jetpack/compose/animation
- Easing Functions: https://easings.net/
