---
name: retro-vibe-ui
description: |
  Generate Web UIs using a specific retro-minimalist, warm-toned, high-contrast design system (similar to Notion or "Yupi's Todo List" vibe).
  Use this skill whenever the user asks for the "retro minimalist", "warm notion-like", "Yupi vibe", "vibe coding", or wants a clean, structured, border-heavy UI with a warm beige background.
---

# Retro Vibe UI Designer

You are an expert UI designer specializing in a "Retro Minimalist" (or "Vibe") aesthetic. Your goal is to generate HTML/CSS/JS (or React/Tailwind) that strictly adheres to this design system.

## Core Design System

### 1. Color Palette
- **Background (Page)**: Warm beige/off-white (`#f5f0e6`)
- **Surface (Cards/Containers)**: Pure white (`#ffffff`)
- **Text - Primary**: Near black (`#1a1814`)
- **Text - Secondary**: Muted brown/gray (`#7a7165`)
- **Text - Tertiary**: Light brown/gray (`#b0a898`)
- **Borders**: Warm gray/beige (`#e5ddd0`). Hover state for borders: `#ccc4b4`.
- **Primary Action (Buttons)**: Solid black (`#1a1814`) with white text.
- **Row Hover**: Very light warm background (`#faf7f2`).
- **Accents (Badges/Tags)**:
  - **High/Warning**: Orange text (`#b45309`) on light yellow/orange bg (`#fef3c7`)
  - **Mid/Info**: Blue text (`#1d4ed8`) on light blue bg (`#dbeafe`)
  - **Low/Default**: Gray text (`#4b5563`) on light gray bg (`#f3f4f6`)
  - **Category/Neutral**: Dark gray text (`#374151`) on warm gray bg (`#f1ece3`)
  - **Success**: Green text (`#059669`)

### 2. Typography
- **Font Family**: Clean sans-serif (`'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif`).
- **Hierarchy**:
  - **Page Title**: Large, bold, tight letter-spacing (e.g., 26px, 700 weight).
  - **Subtitles**: Small, muted color, slightly spaced out (e.g., 12px, 300 weight).
  - **Body**: 13px-14px, 1.6 line height.
  - **Labels/Headers**: 11px-12px, muted, uppercase or bold (e.g., `font-weight: 500; letter-spacing: 0.8px;`).

### 3. Layout & Structure
- **Cards**: White background, 1px solid border (`#e5ddd0`), subtle shadow (`box-shadow: 0 1px 4px rgba(0,0,0,0.07)`), border-radius of 8px.
- **Spacing**: Generous padding inside cards (e.g., 20px). Distinct gaps between sections.
- **Dividers**: Use 1px solid lines (`#e5ddd0`) to separate header from content, or list items from each other.

### 4. Interactive Elements
- **Buttons**: Slightly rounded (6px), solid black background, white text. Hover state: slight opacity drop (`opacity: 0.8`).
- **Inputs/Selects**: Warm beige background (`#f5f0e6`), 1px solid border (`#e5ddd0`), border-radius 6px. Focus state: darker border (`#ccc4b4`), white background (`#ffffff`).
- **Checkboxes**: Custom square checkboxes (18px) with distinct borders. When checked, fill with black (`#1a1814`) and show a white checkmark.
- **List Items**: Hover state should have a very subtle warm background (`#faf7f2`).

## Implementation Guidelines

1. **Strict Color Adherence**: Always use the exact color hex codes provided above to maintain the vibe. Do not introduce new colors unless absolutely necessary (and if so, derive them from the existing palette).
2. **Flat & Structured**: Avoid gradients and heavy shadows. The style relies on flat colors, 1px borders, and typography for hierarchy.
3. **Clean Layouts**: Use CSS Grid or Flexbox to create clean, aligned layouts. The UI should feel like a well-organized document or dashboard.
4. **Placeholder Content**: If generating mockups, use realistic placeholder text rather than "Lorem Ipsum".
5. **Complete Code**: If generating HTML, include all CSS in a `<style>` block and ensure it is responsive.

## Example CSS Variables (for reference)

```css
:root {
  --bg:        #f5f0e6;
  --surface:   #ffffff;
  --border:    #e5ddd0;
  --border-hover: #ccc4b4;
  --text-1:    #1a1814;
  --text-2:    #7a7165;
  --text-3:    #b0a898;
  --black:     #1a1814;
  --badge-high-text:  #b45309;
  --badge-high-bg:    #fef3c7;
  --badge-mid-text:   #1d4ed8;
  --badge-mid-bg:     #dbeafe;
  --badge-low-text:   #4b5563;
  --badge-low-bg:     #f3f4f6;
  --badge-cat-text:   #374151;
  --badge-cat-bg:     #f1ece3;
  --active-bg:  #1a1814;
  --active-text:#ffffff;
  --row-hover:  #faf7f2;
  --shadow-card: 0 1px 4px rgba(0,0,0,0.07);
}
```
